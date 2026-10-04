package com.fiap.sast.analysis;

import com.fiap.sast.github.GitHubClient;
import com.fiap.sast.persistence.*;
import com.fiap.sast.semantic.SemanticAnalysisService;
import com.fiap.sast.semantic.SemanticSuggestionService;
import jakarta.annotation.PreDestroy;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** Executa uma tentativa inteira depois que um consumidor assumiu a task no PostgreSQL. */
@Service
public class AnalysisJobService {
    private static final Duration LEASE = Duration.ofSeconds(60);
    private final GitHubClient git; private final SastEngine engine; private final AnalysisRepository analyses;
    private final SemanticAnalysisService semantic; private final SemanticSuggestionService suggestions;
    private final ObjectMapper mapper; private final TransactionTemplate transactions;
    private final ScheduledExecutorService renewals = Executors.newScheduledThreadPool(2);

    public AnalysisJobService(GitHubClient git, SastEngine engine, AnalysisRepository analyses,
            SemanticAnalysisService semantic, SemanticSuggestionService suggestions, ObjectMapper mapper,
            PlatformTransactionManager transactionManager) {
        this.git = git; this.engine = engine; this.analyses = analyses; this.semantic = semantic;
        this.suggestions = suggestions; this.mapper = mapper; this.transactions = new TransactionTemplate(transactionManager);
    }

    /** Compatibilidade para chamadas antigas; a produção publica pela outbox. */
    public void submit(UUID id, String ignoredUrl, String ignoredReference) {
        String owner = "legacy-" + UUID.randomUUID(); if (claim(id, owner)) processClaimed(id, owner);
    }

    public boolean claim(UUID id, String workerId) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            var now = Instant.now();
            if (analyses.claim(id, workerId, now.plus(LEASE), now) != 1) return false;
            var a = analyses.findById(id).orElseThrow();
            // Cada tentativa substitui findings e sugestões parciais da tentativa anterior.
            a.findings.clear(); a.suggestions.clear(); a.filesProcessed = 0; a.filesTotal = 0;
            a.snapshotHash = null; a.semanticStatus = "PENDING"; a.suggestionStatus = "PENDING";
            a.failureStage = null; a.failureMessage = null; a.stage = "QUEUED"; analyses.save(a); return true;
        }));
    }

    public void processClaimed(UUID id, String workerId) {
        var renewal = renewals.scheduleAtFixedRate(() -> renewLease(id, workerId), 15, 15, TimeUnit.SECONDS);
        try { run(id); } finally { renewal.cancel(false); }
    }

    private void run(UUID id) {
        try {
            var a0 = transactions.execute(status -> analyses.findById(id).orElseThrow());
            update(id, a -> a.stage = "DOWNLOADING");
            String sha = a0.commitSha;
            if (sha == null) { sha = git.resolveCommitSha(a0.repositoryUrl, a0.reference); final String fixed = sha; update(id, a -> a.commitSha = fixed); }
            var snapshot = git.download(a0.repositoryUrl, sha);
            if (snapshot.files().isEmpty()) throw new IllegalStateException("O repositório não contém arquivos Java elegíveis");
            update(id, a -> { a.filesTotal = snapshot.files().size(); a.filesAnalyzed = snapshot.files().size();
                a.snapshotHash = snapshotHash(snapshot.files()); a.stage = "DETERMINISTIC"; });
            var candidates = new ArrayList<SemanticAnalysisService.Candidate>();
            for (var file : snapshot.files()) {
                var findings = engine.analyze(file.content(), file.path());
                transactions.executeWithoutResult(status -> {
                    var a = analyses.findById(id).orElseThrow();
                    findings.forEach(value -> { var f = new Finding(); f.analysis = a; f.ruleId = value.ruleId(); f.title = value.title();
                        f.severity = value.severity(); f.cwe = value.cwe(); f.description = value.description(); f.fileName = value.fileName();
                        f.line = value.line(); f.column = value.column(); f.snippet = value.snippet(); f.taintTrace = writeTrace(value.taintTrace());
                        a.findings.add(f); candidates.add(new SemanticAnalysisService.Candidate(f.id, value, file.content())); });
                    a.filesProcessed++; analyses.save(a);
                });
            }
            update(id, a -> { a.semanticStatus = candidates.isEmpty() ? "NOT_APPLICABLE" : "RUNNING"; a.stage = candidates.isEmpty() ? "SUGGESTIONS" : "SEMANTIC"; });
            var enriched = semantic.enrich(candidates);
            transactions.executeWithoutResult(status -> { var a = analyses.findById(id).orElseThrow(); a.semanticStatus = enriched.status();
                enriched.assessments().forEach((findingId, assessment) -> a.findings.stream().filter(f -> f.id.equals(findingId)).findFirst()
                    .ifPresent(f -> f.aiAssessment = AiAssessmentEntity.from(f, assessment)));
                a.suggestionStatus = "RUNNING"; a.stage = "SUGGESTIONS"; analyses.save(a); });
            var sourceFiles = snapshot.files().stream().map(file -> new SemanticSuggestionService.SourceFile(file.path(), file.content())).toList();
            Consumer<com.fiap.sast.semantic.AiSuggestion> persist = s -> transactions.executeWithoutResult(status -> { var a = analyses.findById(id).orElseThrow(); replaceSuggestion(a, s); analyses.save(a); });
            var result = suggestions.scan(sourceFiles, persist);
            update(id, a -> { a.suggestionStatus = result.status(); a.status = "COMPLETED"; a.stage = "COMPLETED";
                a.leaseOwner = null; a.leaseUntil = null; a.nextAttemptAt = null; });
        } catch (Exception failure) { handleFailure(id, failure); }
    }

    private void handleFailure(UUID id, Exception failure) {
        String message = safeMessage(failure);
        transactions.executeWithoutResult(status -> analyses.findById(id).ifPresent(a -> {
            boolean retryable = failure instanceof GitHubClient.UnavailableException
                    || failure instanceof GitHubClient.RateLimitException;
            if (!retryable || a.attemptCount >= 3) { fail(a, a.stage, message); a.leaseOwner = null; a.leaseUntil = null; return; }
            int[] seconds = {30, 120, 300}; a.nextAttemptAt = Instant.now().plusSeconds(seconds[Math.min(a.attemptCount - 1, 2)]);
            a.leaseUntil = a.nextAttemptAt; a.failureStage = a.stage; a.failureMessage = message;
        }));
    }

    private void renewLease(UUID id, String owner) { transactions.executeWithoutResult(status -> analyses.findById(id).ifPresent(a -> {
        if (owner.equals(a.leaseOwner) && "PROCESSING".equals(a.status)) { a.leaseUntil = Instant.now().plus(LEASE); analyses.save(a); }
    })); }
    private void update(UUID id, Consumer<Analysis> change) { transactions.executeWithoutResult(status -> analyses.findById(id).ifPresent(a -> { change.accept(a); analyses.save(a); })); }
    private static void fail(Analysis a, String stage, String message) { a.status = "FAILED"; a.stage = "FAILED"; a.failureStage = stage; a.failureMessage = message.length() > 500 ? message.substring(0, 500) : message; }

    static void replaceSuggestion(Analysis a, com.fiap.sast.semantic.AiSuggestion s) {
        a.suggestions.removeIf(existing -> Objects.equals(existing.fileName, s.fileName()) && existing.lineNumber == s.line());
        a.suggestions.add(AiSuggestionEntity.from(a, s));
    }
    private static String safeMessage(Exception e) {
        if (e instanceof IllegalStateException && e.getMessage() != null) return e.getMessage();
        if (e instanceof GitHubClient.RateLimitException) return "O GitHub limitou temporariamente a requisição";
        if (e instanceof GitHubClient.UnavailableException) return "Não foi possível obter o snapshot do GitHub";
        return "A análise foi interrompida por uma falha interna";
    }
    private String writeTrace(TaintTrace t) { if (t == null) return null; try { return mapper.writeValueAsString(t); } catch (tools.jackson.core.JacksonException e) { throw new IllegalStateException("Trace inválido", e); } }
    private static String snapshotHash(List<GitHubClient.File> files) { try { var d = MessageDigest.getInstance("SHA-256"); files.stream().sorted(Comparator.comparing(GitHubClient.File::path)).forEach(f -> { d.update(f.path().getBytes(java.nio.charset.StandardCharsets.UTF_8)); d.update((byte)0); d.update(f.content().getBytes(java.nio.charset.StandardCharsets.UTF_8)); d.update((byte)0); }); return HexFormat.of().formatHex(d.digest()); } catch (Exception e) { throw new IllegalStateException(e); } }
    @PreDestroy void stop() { renewals.shutdownNow(); }
}
