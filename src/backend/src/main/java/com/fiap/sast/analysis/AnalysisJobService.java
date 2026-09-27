package com.fiap.sast.analysis;

import com.fiap.sast.github.GitHubClient;
import com.fiap.sast.persistence.AiAssessmentEntity;
import com.fiap.sast.persistence.AiSuggestionEntity;
import com.fiap.sast.persistence.Analysis;
import com.fiap.sast.persistence.AnalysisRepository;
import com.fiap.sast.persistence.Finding;
import com.fiap.sast.semantic.SemanticAnalysisService;
import com.fiap.sast.semantic.SemanticSuggestionService;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** Orquestra análises fora da requisição e publica progresso persistido para polling. */
@Service
public class AnalysisJobService {
    private final GitHubClient git;
    private final SastEngine engine;
    private final AnalysisRepository analyses;
    private final SemanticAnalysisService semantic;
    private final SemanticSuggestionService suggestions;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactions;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public AnalysisJobService(GitHubClient git, SastEngine engine, AnalysisRepository analyses,
            SemanticAnalysisService semantic, SemanticSuggestionService suggestions,
            ObjectMapper mapper, PlatformTransactionManager transactionManager) {
        this.git = git;
        this.engine = engine;
        this.analyses = analyses;
        this.semantic = semantic;
        this.suggestions = suggestions;
        this.mapper = mapper;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @PostConstruct
    void markInterruptedJobs() {
        transactions.executeWithoutResult(status -> analyses.findAll().stream()
                .filter(analysis -> "PROCESSING".equals(analysis.status))
                .forEach(analysis -> fail(analysis, "WORKER", "A análise foi interrompida pela reinicialização da API")));
    }

    @PreDestroy
    void stop() {
        executor.shutdownNow();
    }

    public void submit(UUID analysisId, String repositoryUrl, String reference) {
        try {
            executor.submit(() -> run(analysisId, repositoryUrl, reference));
        } catch (RejectedExecutionException rejected) {
            transactions.executeWithoutResult(status -> analyses.findById(analysisId)
                    .ifPresent(analysis -> fail(analysis, "QUEUED", "A fila de análises está temporariamente indisponível")));
        }
    }

    private void run(UUID id, String repositoryUrl, String reference) {
        try {
            update(id, analysis -> { analysis.stage = "DOWNLOADING"; });
            var snapshot = git.download(repositoryUrl, reference);
            if (snapshot.files().isEmpty()) throw new IllegalStateException("O repositório não contém arquivos Java elegíveis");
            update(id, analysis -> {
                analysis.filesTotal = snapshot.files().size();
                analysis.filesAnalyzed = snapshot.files().size();
                analysis.filesProcessed = 0;
                analysis.snapshotHash = snapshotHash(snapshot.files());
                analysis.stage = "DETERMINISTIC";
            });

            var candidates = new ArrayList<SemanticAnalysisService.Candidate>();
            int processed = 0;
            for (var file : snapshot.files()) {
                var findings = engine.analyze(file.content(), file.path());
                transactions.executeWithoutResult(status -> {
                    var analysis = analyses.findById(id).orElseThrow();
                    findings.forEach(value -> {
                        var finding = new Finding();
                        finding.analysis = analysis;
                        finding.ruleId = value.ruleId(); finding.title = value.title(); finding.severity = value.severity();
                        finding.cwe = value.cwe(); finding.description = value.description(); finding.fileName = value.fileName();
                        finding.line = value.line(); finding.column = value.column(); finding.snippet = value.snippet();
                        finding.taintTrace = writeTrace(value.taintTrace());
                        analysis.findings.add(finding);
                        candidates.add(new SemanticAnalysisService.Candidate(finding.id, value, file.content()));
                    });
                    analysis.filesProcessed = analysis.filesProcessed + 1;
                    analyses.save(analysis);
                });
                processed++;
            }

            update(id, analysis -> {
                analysis.semanticStatus = candidates.isEmpty() ? "NOT_APPLICABLE" : "RUNNING";
                analysis.stage = candidates.isEmpty() ? "SUGGESTIONS" : "SEMANTIC";
            });
            var enriched = semantic.enrich(candidates);
            transactions.executeWithoutResult(status -> {
                var analysis = analyses.findById(id).orElseThrow();
                analysis.semanticStatus = enriched.status();
                enriched.assessments().forEach((findingId, assessment) -> analysis.findings.stream()
                        .filter(finding -> finding.id.equals(findingId)).findFirst()
                        .ifPresent(finding -> finding.aiAssessment = AiAssessmentEntity.from(finding, assessment)));
                analysis.suggestionStatus = "RUNNING";
                analysis.stage = "SUGGESTIONS";
                analyses.save(analysis);
            });

            List<SemanticSuggestionService.SourceFile> sourceFiles = snapshot.files().stream()
                    .map(file -> new SemanticSuggestionService.SourceFile(file.path(), file.content())).toList();
            Consumer<com.fiap.sast.semantic.AiSuggestion> persist = suggestion -> transactions.executeWithoutResult(status -> {
                var analysis = analyses.findById(id).orElseThrow();
                analysis.suggestions.add(AiSuggestionEntity.from(analysis, suggestion));
                analyses.save(analysis);
            });
            var suggestionResult = suggestions.scan(sourceFiles, persist);
            update(id, analysis -> {
                analysis.suggestionStatus = suggestionResult.status();
                analysis.status = "COMPLETED";
                analysis.stage = "COMPLETED";
                analysis.failureStage = null;
                analysis.failureMessage = null;
            });
        } catch (Exception failure) {
            String stage = transactions.execute(status -> analyses.findById(id).map(a -> a.stage).orElse("WORKER"));
            transactions.executeWithoutResult(status -> analyses.findById(id)
                    .ifPresent(analysis -> fail(analysis, stage, safeMessage(failure))));
        }
    }

    private void update(UUID id, Consumer<Analysis> change) {
        transactions.executeWithoutResult(status -> analyses.findById(id).ifPresent(analysis -> {
            change.accept(analysis);
            analyses.save(analysis);
        }));
    }

    private static void fail(Analysis analysis, String stage, String message) {
        analysis.status = "FAILED";
        analysis.stage = "FAILED";
        analysis.failureStage = stage;
        analysis.failureMessage = message.length() > 500 ? message.substring(0, 500) : message;
    }

    private static String safeMessage(Exception failure) {
        if (failure instanceof IllegalStateException && failure.getMessage() != null) return failure.getMessage();
        if (failure instanceof GitHubClient.RateLimitException) return "O GitHub limitou temporariamente a requisição";
        if (failure instanceof GitHubClient.UnavailableException) return "Não foi possível obter o snapshot do GitHub";
        return "A análise foi interrompida por uma falha interna";
    }

    private String writeTrace(TaintTrace trace) {
        if (trace == null) return null;
        try { return mapper.writeValueAsString(trace); }
        catch (tools.jackson.core.JacksonException failure) { throw new IllegalStateException("Trace inválido", failure); }
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
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }
}
