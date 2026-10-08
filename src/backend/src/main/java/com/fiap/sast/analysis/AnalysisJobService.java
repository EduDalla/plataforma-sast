package com.fiap.sast.analysis;

import com.fiap.sast.github.GitHubClient;
import com.fiap.sast.persistence.AiAssessmentEntity;
import com.fiap.sast.persistence.AiSuggestionEntity;
import com.fiap.sast.persistence.Analysis;
import com.fiap.sast.persistence.AnalysisRepository;
import com.fiap.sast.persistence.Finding;
import com.fiap.sast.semantic.SemanticAnalysisService;
import com.fiap.sast.semantic.SemanticSuggestionService;
import jakarta.annotation.PreDestroy;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** Executa uma tentativa inteira depois que um consumidor assumiu a task no PostgreSQL. */
@Service
public class AnalysisJobService {
    private static final Duration LEASE = Duration.ofSeconds(60);
    private final GitHubClient git;
    private final SastEngine engine;
    private final AnalysisRepository analyses;
    private final SemanticAnalysisService semantic;
    private final SemanticSuggestionService suggestions;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactions;
    private final EntityManager entityManager;
    private final ScheduledExecutorService renewals = Executors.newScheduledThreadPool(2);

    private static final class LeaseLostException extends RuntimeException {
        private LeaseLostException() { super("A concessão da tentativa foi perdida"); }
    }

    /**
     * Inicializa o processamento do worker com dependências de análise e persistência.
     *
     * @param git cliente de snapshots públicos do GitHub
     * @param engine motor de findings determinísticos
     * @param analyses repositório de análises e concessões
     * @param semantic avaliação consultiva de findings
     * @param suggestions sugestões consultivas independentes
     * @param mapper serializador dos traces persistidos
     * @param transactionManager gerenciador de transações curtas do worker
     * @param entityManager acesso transacional para aplicar lock de fencing
     */
    public AnalysisJobService(GitHubClient git, SastEngine engine, AnalysisRepository analyses,
            SemanticAnalysisService semantic, SemanticSuggestionService suggestions, ObjectMapper mapper,
            PlatformTransactionManager transactionManager, EntityManager entityManager) {
        this.git = git;
        this.engine = engine;
        this.analyses = analyses;
        this.semantic = semantic;
        this.suggestions = suggestions;
        this.mapper = mapper;
        this.transactions = new TransactionTemplate(transactionManager);
        this.entityManager = entityManager;
    }

    /**
     * Executa uma chamada legada após assumir a tarefa; a produção publica pela outbox.
     *
     * @param id identificador da análise
     * @param ignoredUrl URL legada ignorada, pois a análise persistida define a origem
     * @param ignoredReference referência legada ignorada
     */
    public void submit(UUID id, String ignoredUrl, String ignoredReference) {
        String owner = "legacy-" + UUID.randomUUID();
        if (claim(id, owner)) processClaimed(id, owner);
    }

    /**
     * Assume uma tentativa de análise no PostgreSQL e limpa dados parciais anteriores.
     *
     * @param id identificador da análise
     * @param workerId identificador do worker candidato
     * @return {@code true} quando a concessão foi obtida
     */
    public boolean claim(UUID id, String workerId) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            var now = Instant.now();
            if (analyses.claim(id, workerId, now.plus(LEASE), now) != 1) return false;
            var a = analyses.findById(id).orElseThrow();
            // Cada tentativa substitui findings e sugestões parciais da tentativa anterior.
            a.findings.clear();
            a.suggestions.clear();
            a.filesProcessed = 0;
            a.filesTotal = 0;
            a.snapshotHash = null;
            a.semanticStatus = "PENDING";
            a.suggestionStatus = "PENDING";
            a.failureStage = null;
            a.failureMessage = null;
            a.stage = "QUEUED";
            analyses.save(a);
            return true;
        }));
    }

    /**
     * Processa a tentativa assumida e renova a concessão enquanto ela estiver ativa.
     *
     * @param id identificador da análise
     * @param workerId identificador do worker proprietário da concessão
     */
    public void processClaimed(UUID id, String workerId) {
        var renewal = renewals.scheduleAtFixedRate(() -> renewLease(id, workerId), 15, 15, TimeUnit.SECONDS);
        try {
            run(id, workerId);
        } finally {
            renewal.cancel(false);
        }
    }

    private void run(UUID id, String workerId) {
        try (var parseCache = engine.newParseCache()) {
            var a0 = transactions.execute(status -> analyses.findById(id).orElseThrow());
            update(id, workerId, a -> a.stage = "DOWNLOADING");
            String sha = a0.commitSha;
            if (sha == null) {
                sha = git.resolveCommitSha(a0.repositoryUrl, a0.reference);
                final String fixed = sha;
                update(id, workerId, a -> a.commitSha = fixed);
            }
            var snapshot = git.download(a0.repositoryUrl, sha);
            if (snapshot.files().isEmpty()) {
                throw new IllegalStateException("O repositório não contém arquivos Java elegíveis");
            }
            update(id, workerId, a -> {
                a.filesTotal = snapshot.files().size();
                a.filesAnalyzed = snapshot.files().size();
                a.snapshotHash = snapshotHash(snapshot.files());
                a.stage = "DETERMINISTIC";
            });
            var candidates = new ArrayList<SemanticAnalysisService.Candidate>();
            for (var file : snapshot.files()) {
                var findings = engine.analyze(file.content(), file.path(), parseCache);
                transactions.executeWithoutResult(status -> {
                    var a = lockedOwned(id, workerId);
                    findings.forEach(value -> {
                        var f = new Finding();
                        f.analysis = a;
                        f.ruleId = value.ruleId();
                        f.title = value.title();
                        f.severity = value.severity();
                        f.cwe = value.cwe();
                        f.description = value.description();
                        f.fileName = value.fileName();
                        f.line = value.line();
                        f.column = value.column();
                        f.snippet = value.snippet();
                        f.taintTrace = writeTrace(value.taintTrace());
                        a.findings.add(f);
                        candidates.add(new SemanticAnalysisService.Candidate(f.id, value, file.content()));
                    });
                    a.filesProcessed++;
                    analyses.save(a);
                });
            }
            update(id, workerId, a -> {
                a.semanticStatus = candidates.isEmpty() ? "NOT_APPLICABLE" : "RUNNING";
                a.stage = candidates.isEmpty() ? "SUGGESTIONS" : "SEMANTIC";
            });
            var enriched = semantic.enrich(candidates, parseCache);
            transactions.executeWithoutResult(status -> {
                var a = lockedOwned(id, workerId);
                a.semanticStatus = enriched.status();
                enriched.assessments().forEach((findingId, assessment) -> a.findings.stream()
                        .filter(f -> f.id.equals(findingId)).findFirst()
                        .ifPresent(f -> f.aiAssessment = AiAssessmentEntity.from(f, assessment)));
                a.suggestionStatus = "RUNNING";
                a.stage = "SUGGESTIONS";
                analyses.save(a);
            });
            var sourceFiles = snapshot.files().stream()
                    .map(file -> new SemanticSuggestionService.SourceFile(file.path(), file.content())).toList();
            Consumer<com.fiap.sast.semantic.AiSuggestion> persist = suggestion ->
                    transactions.executeWithoutResult(status -> {
                        var a = lockedOwned(id, workerId);
                        replaceSuggestion(a, suggestion);
                        analyses.save(a);
                    });
            var result = suggestions.scan(sourceFiles, persist, parseCache);
            update(id, workerId, a -> {
                a.suggestionStatus = result.status();
                a.status = "COMPLETED";
                a.stage = "COMPLETED";
                a.leaseOwner = null;
                a.leaseUntil = null;
                a.nextAttemptAt = null;
            });
        } catch (Exception failure) {
            handleFailure(id, workerId, failure);
        }
    }

    private void handleFailure(UUID id, String workerId, Exception failure) {
        String message = safeMessage(failure);
        transactions.executeWithoutResult(status -> {
            var a = locked(id);
            if (a == null || !workerId.equals(a.leaseOwner)) return;
            boolean retryable = failure instanceof GitHubClient.UnavailableException
                    || failure instanceof GitHubClient.RateLimitException;
            if (!retryable || a.attemptCount >= 3) {
                fail(a, a.stage, message);
                a.leaseOwner = null;
                a.leaseUntil = null;
                return;
            }
            int[] seconds = {30, 120, 300};
            a.nextAttemptAt = Instant.now().plusSeconds(seconds[Math.min(a.attemptCount - 1, 2)]);
            a.leaseUntil = a.nextAttemptAt;
            a.failureStage = a.stage;
            a.failureMessage = message;
        });
    }

    private void renewLease(UUID id, String owner) {
        transactions.executeWithoutResult(status -> analyses.findById(id).ifPresent(a -> {
            if (owner.equals(a.leaseOwner) && "PROCESSING".equals(a.status)) {
                a.leaseUntil = Instant.now().plus(LEASE);
                analyses.save(a);
            }
        }));
    }

    private void update(UUID id, String workerId, Consumer<Analysis> change) {
        transactions.executeWithoutResult(status -> {
            var a = locked(id);
            if (a == null) return;
            if (!workerId.equals(a.leaseOwner)) throw new LeaseLostException();
            change.accept(a);
            analyses.save(a);
        });
    }

    private Analysis lockedOwned(UUID id, String workerId) {
        var analysis = locked(id);
        if (analysis == null) throw new IllegalStateException("Análise inexistente");
        if (!workerId.equals(analysis.leaseOwner)) throw new LeaseLostException();
        return analysis;
    }

    private Analysis locked(UUID id) {
        return entityManager.find(Analysis.class, id, LockModeType.PESSIMISTIC_WRITE);
    }

    private static void fail(Analysis analysis, String stage, String message) {
        analysis.status = "FAILED";
        analysis.stage = "FAILED";
        analysis.failureStage = stage;
        analysis.failureMessage = message.length() > 500 ? message.substring(0, 500) : message;
    }

    static void replaceSuggestion(Analysis a, com.fiap.sast.semantic.AiSuggestion s) {
        a.suggestions.removeIf(existing -> Objects.equals(existing.fileName, s.fileName())
                && existing.lineNumber == s.line());
        a.suggestions.add(AiSuggestionEntity.from(a, s));
    }

    private static String safeMessage(Exception e) {
        if (e instanceof IllegalStateException && e.getMessage() != null) return e.getMessage();
        if (e instanceof GitHubClient.RateLimitException) return "O GitHub limitou temporariamente a requisição";
        if (e instanceof GitHubClient.UnavailableException) return "Não foi possível obter o snapshot do GitHub";
        return "A análise foi interrompida por uma falha interna";
    }

    private String writeTrace(TaintTrace trace) {
        if (trace == null) return null;
        try {
            return mapper.writeValueAsString(trace);
        } catch (tools.jackson.core.JacksonException failure) {
            throw new IllegalStateException("Trace inválido", failure);
        }
    }

    private static String snapshotHash(List<GitHubClient.File> files) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            files.stream().sorted(Comparator.comparing(GitHubClient.File::path)).forEach(file -> {
                digest.update(file.path().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(file.content().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                digest.update((byte) 0);
            });
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    @PreDestroy
    void stop() {
        renewals.shutdownNow();
    }
}
