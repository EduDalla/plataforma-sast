package com.fiap.sast.web;

import com.fiap.sast.analysis.SastEngine;
import com.fiap.sast.analysis.AnalysisJobService;
import com.fiap.sast.analysis.SecurityFinding;
import com.fiap.sast.analysis.TaintTrace;
import com.fiap.sast.auth.UserRepository;
import com.fiap.sast.github.GitHubClient;
import com.fiap.sast.persistence.AiAssessmentEntity;
import com.fiap.sast.persistence.AiSuggestionEntity;
import com.fiap.sast.persistence.Analysis;
import com.fiap.sast.persistence.AnalysisRepository;
import com.fiap.sast.persistence.AnalysisOutboxEvent;
import com.fiap.sast.persistence.AnalysisOutboxRepository;
import com.fiap.sast.persistence.Finding;
import com.fiap.sast.semantic.AiAssessment;
import com.fiap.sast.semantic.AiSuggestion;
import com.fiap.sast.semantic.SemanticAnalysisService;
import com.fiap.sast.semantic.SemanticSuggestionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import java.net.URI;
import java.security.Principal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/analyses")
public class AnalysisController {
    private static final Logger log = LoggerFactory.getLogger(AnalysisController.class);

    private final GitHubClient git;
    private final SastEngine engine;
    private final AnalysisRepository repo;
    private final UserRepository users;
    private final ObjectMapper mapper;
    private final SemanticAnalysisService semantic;
    private final SemanticSuggestionService suggestionService;
    private final AnalysisJobService jobs;
    private final AnalysisOutboxRepository outbox;
    private final CompletedResultCache completedResults;

    /**
     * Cria uma instância para chamadas legadas e testes sem publicador de outbox.
     *
     * @param git cliente do GitHub
     * @param engine motor de regras determinísticas
     * @param repo repositório de análises
     * @param users repositório de usuários
     * @param mapper serializador JSON
     * @param semantic avaliação consultiva de findings
     * @param suggestionService sugestões consultivas
     * @param jobs processador de tarefas
     */
    public AnalysisController(GitHubClient git, SastEngine engine, AnalysisRepository repo, UserRepository users,
            ObjectMapper mapper, SemanticAnalysisService semantic, SemanticSuggestionService suggestionService,
            AnalysisJobService jobs) {
        this(git, engine, repo, users, mapper, semantic, suggestionService, jobs, null,
                new CompletedResultCache());
    }

    /**
     * Cria uma instância com outbox e cache local usado em chamadas diretas.
     *
     * @param git cliente do GitHub
     * @param engine motor de regras determinísticas
     * @param repo repositório de análises
     * @param users repositório de usuários
     * @param mapper serializador JSON
     * @param semantic avaliação consultiva de findings
     * @param suggestionService sugestões consultivas
     * @param jobs processador de tarefas
     * @param outbox repositório de eventos para publicação
     */
    public AnalysisController(GitHubClient git, SastEngine engine, AnalysisRepository repo, UserRepository users,
            ObjectMapper mapper, SemanticAnalysisService semantic, SemanticSuggestionService suggestionService,
            AnalysisJobService jobs, AnalysisOutboxRepository outbox) {
        this(git, engine, repo, users, mapper, semantic, suggestionService, jobs, outbox,
                new CompletedResultCache());
    }

    /**
     * Cria o controlador de produção com cache compartilhado pela instância da API.
     *
     * @param git cliente do GitHub
     * @param engine motor de regras determinísticas
     * @param repo repositório de análises
     * @param users repositório de usuários
     * @param mapper serializador JSON
     * @param semantic avaliação consultiva de findings
     * @param suggestionService sugestões consultivas
     * @param jobs processador de tarefas
     * @param outbox repositório de eventos para publicação
     * @param completedResults cache local de resultados concluídos
     */
    @Autowired
    public AnalysisController(GitHubClient git, SastEngine engine, AnalysisRepository repo, UserRepository users,
            ObjectMapper mapper, SemanticAnalysisService semantic, SemanticSuggestionService suggestionService,
            AnalysisJobService jobs, AnalysisOutboxRepository outbox, CompletedResultCache completedResults) {
        this.git = git;
        this.engine = engine;
        this.repo = repo;
        this.users = users;
        this.mapper = mapper;
        this.semantic = semantic;
        this.suggestionService = suggestionService;
        this.jobs = jobs;
        this.outbox = outbox;
        this.completedResults = completedResults;
    }

    public record Request(@NotBlank String repositoryUrl, String reference) {}

    public record FindingResult(String ruleId, String title, String severity, String cwe,
            String description, String fileName, int line, int column, String snippet,
            TaintTrace taintTrace, AiAssessment aiAssessment) {}

    public record ResultSummary(int total, int critical, int high, int medium, int low,
            int unclassified, String highestPriority) {}

    public record Result(UUID analysisId, String status, String repositoryUrl, String reference,
            String language, int filesAnalyzed, Instant createdAt, String semanticStatus,
            String suggestionStatus, List<AiSuggestion> suggestions, List<FindingResult> findings,
            ResultSummary resultSummary,
            String stage, int filesProcessed, int filesTotal, String failureStage, String failureMessage,
            String commitSha) {}

    private Result toResult(Analysis analysis) {
        var findings = analysis.findings.stream()
                .map(finding -> new FindingResult(
                        finding.ruleId,
                        finding.title,
                        finding.severity,
                        finding.cwe,
                        finding.description,
                        finding.fileName,
                        finding.line,
                        finding.column,
                        finding.snippet,
                        readTaintTrace(finding.taintTrace),
                        finding.aiAssessment == null ? null : finding.aiAssessment.toValue()))
                .sorted(Comparator.comparing(FindingResult::fileName)
                        .thenComparingInt(FindingResult::line)
                        .thenComparingInt(FindingResult::column)
                        .thenComparing(FindingResult::ruleId))
                .toList();
        var suggestions = analysis.suggestions.stream().map(AiSuggestionEntity::toValue)
                .sorted(Comparator.comparing(AiSuggestion::fileName).thenComparingInt(AiSuggestion::line))
                .toList();
        return new Result(analysis.id, analysis.status, analysis.repositoryUrl, analysis.reference,
                analysis.language, analysis.filesAnalyzed, analysis.createdAt, analysis.semanticStatus,
                analysis.suggestionStatus, suggestions, findings, summarize(findings, suggestions),
                analysis.stage, analysis.filesProcessed,
                analysis.filesTotal, analysis.failureStage, analysis.failureMessage, analysis.commitSha);
    }

    private TaintTrace readTaintTrace(String json) {
        if (json == null) {
            return null;
        }
        try {
            return mapper.readValue(json, TaintTrace.class);
        } catch (tools.jackson.core.JacksonException e) {
            throw new IllegalStateException("taintTrace persistido é inválido", e);
        }
    }

    private String writeTaintTrace(TaintTrace trace) {
        if (trace == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(trace);
        } catch (tools.jackson.core.JacksonException e) {
            throw new IllegalStateException("Não foi possível serializar o taintTrace", e);
        }
    }

    public record HistoryEntry(UUID analysisId, String reference, Instant createdAt, int filesAnalyzed,
            int findings, ResultSummary resultSummary, int suggestions, String commitSha,
            String coverageStatus, String semanticStatus, String suggestionStatus) {}
    public record SystemCard(String owner, String repositoryName, String repositoryUrl,
            Instant latestCreatedAt, int totalAnalyses, HistoryEntry latest) {}
    public record SystemsPage(List<SystemCard> systems, int page, int size, int totalSystems,
            int totalAnalyses, int totalFindings, int totalCritical, int totalFiles,
            ResultSummary resultSummary) {}
    public record HistoryPage(String owner, String repositoryName, List<HistoryEntry> history,
            int page, int size, int total) {}
    public record Task(UUID analysisId, String status, String stage, String repositoryUrl,
            Instant createdAt, String semanticStatus, String suggestionStatus,
            ResultSummary resultSummary, boolean acknowledged) {}

    private static HistoryEntry history(Analysis a) {
        return new HistoryEntry(a.id, a.reference, a.createdAt, a.filesAnalyzed, a.findings.size(),
                summarizeFindings(a.findings), a.suggestions.size(), a.commitSha, coverageStatus(a),
                a.semanticStatus, a.suggestionStatus);
    }

    /**
     * Classifica a cobertura da execução sem transformar etapa incompleta em ausência de vulnerabilidades.
     *
     * @param analysis execução concluída ou em processamento
     * @return estado confirmado, parcial ou com falha
     */
    private static String coverageStatus(Analysis analysis) {
        if ("FAILED".equalsIgnoreCase(analysis.status)) {
            return "FAILED";
        }
        if (!"COMPLETED".equalsIgnoreCase(analysis.status)
                || analysis.filesTotal <= 0 || analysis.filesProcessed != analysis.filesTotal) {
            return "PARTIAL";
        }
        return "CONFIRMED";
    }

    /**
     * Resume exclusivamente os findings determinísticos para a série histórica.
     *
     * @param findings ocorrências produzidas pelas regras locais
     * @return totais por severidade, sem sugestões consultivas da IA
     */
    private static ResultSummary summarizeFindings(List<Finding> findings) {
        int critical = 0, high = 0, medium = 0, low = 0, unclassified = 0;
        for (var finding : findings) {
            switch (java.util.Objects.requireNonNullElse(finding.severity, "")) {
                case "Critical" -> critical++;
                case "High" -> high++;
                case "Medium" -> medium++;
                case "Low" -> low++;
                default -> unclassified++;
            }
        }
        return summary(critical, high, medium, low, unclassified);
    }

    private static ResultSummary summarize(Analysis analysis) {
        int critical = 0, high = 0, medium = 0, low = 0, unclassified = 0;
        for (var finding : analysis.findings) {
            switch (java.util.Objects.requireNonNullElse(finding.severity, "")) {
                case "Critical" -> critical++;
                case "High" -> high++;
                case "Medium" -> medium++;
                case "Low" -> low++;
                default -> unclassified++;
            }
        }
        for (var suggestion : analysis.suggestions) {
            switch (java.util.Objects.requireNonNullElse(suggestion.severity, "")) {
                case "Critical" -> critical++;
                case "High" -> high++;
                case "Medium" -> medium++;
                case "Low" -> low++;
                default -> unclassified++;
            }
        }
        return summary(critical, high, medium, low, unclassified);
    }

    private static ResultSummary summarize(List<FindingResult> findings, List<AiSuggestion> suggestions) {
        int critical = 0, high = 0, medium = 0, low = 0, unclassified = 0;
        for (var finding : findings) {
            switch (java.util.Objects.requireNonNullElse(finding.severity(), "")) {
                case "Critical" -> critical++;
                case "High" -> high++;
                case "Medium" -> medium++;
                case "Low" -> low++;
                default -> unclassified++;
            }
        }
        for (var suggestion : suggestions) {
            switch (java.util.Objects.requireNonNullElse(suggestion.severity(), "")) {
                case "Critical" -> critical++;
                case "High" -> high++;
                case "Medium" -> medium++;
                case "Low" -> low++;
                default -> unclassified++;
            }
        }
        return summary(critical, high, medium, low, unclassified);
    }

    private static ResultSummary summarize(List<String> priorities) {
        int critical = 0, high = 0, medium = 0, low = 0, unclassified = 0;
        for (var priority : priorities) {
            switch (java.util.Objects.requireNonNullElse(priority, "")) {
                case "Critical" -> critical++;
                case "High" -> high++;
                case "Medium" -> medium++;
                case "Low" -> low++;
                default -> unclassified++;
            }
        }
        return summary(critical, high, medium, low, unclassified);
    }

    private static ResultSummary summary(int critical, int high, int medium, int low, int unclassified) {
        int total = critical + high + medium + low + unclassified;
        String highest = critical > 0 ? "Critical" : high > 0 ? "High" : medium > 0 ? "Medium"
                : low > 0 ? "Low" : unclassified > 0 ? "Unclassified" : null;
        return new ResultSummary(total, critical, high, medium, low, unclassified, highest);
    }

    private static String findingsFingerprint(Analysis analysis) {
        return analysis.findings.stream()
                .map(f -> String.join("\u001f", f.ruleId, f.title, f.severity, f.cwe, f.description,
                        f.fileName, Integer.toString(f.line), Integer.toString(f.column), f.snippet, java.util.Objects.requireNonNullElse(f.taintTrace, "")))
                .sorted().collect(Collectors.joining("\u001e"));
    }

    private static List<Analysis> distinctExecutions(List<Analysis> analyses) {
        var known = new HashSet<String>();
        return analyses.stream()
                .filter(analysis -> "COMPLETED".equalsIgnoreCase(analysis.status))
                .filter(analysis -> known.add(analysis.repositoryOwner + "\u001d"
                + analysis.repositoryName + "\u001d" + String.valueOf(analysis.reference) + "\u001d"
                + findingsFingerprint(analysis) + "\u001d" + analysis.semanticStatus + "\u001d"
                + analysis.semanticModel + "\u001d" + analysis.promptVersion + "\u001d"
                + analysis.snapshotHash + "\u001d" + analysis.commitSha + "\u001d"
                + analysis.suggestionStatus)).toList();
    }

    private static String snapshotHash(List<GitHubClient.File> files) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            files.stream().sorted(Comparator.comparing(GitHubClient.File::path)).forEach(file -> {
                digest.update(file.path().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(file.content().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            });
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    /**
     * Monta a página de sistemas concluídos e o resumo global do usuário autenticado.
     *
     * @param page índice da página solicitado
     * @param size quantidade solicitada de sistemas por página
     * @param principal identidade autenticada
     * @return página de sistemas com totais calculados sobre todas as execuções visíveis
     */
    @GetMapping("/systems")
    @Transactional(readOnly = true)
    public SystemsPage systems(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, Principal principal) {
        var owner = users.findByEmail(principal.getName()).orElseThrow();
        page = Math.max(0, page);
        size = Math.min(20, Math.max(1, size));

        var analyses = distinctExecutions(repo.findCompletedByUser(owner.id));
        var grouped = new LinkedHashMap<String, List<Analysis>>();
        analyses.forEach(a -> grouped.computeIfAbsent(a.repositoryOwner + "/" + a.repositoryName,
                ignored -> new ArrayList<>()).add(a));

        var cards = grouped.values().stream().map(items -> {
            var latest = items.get(0);
            return new SystemCard(latest.repositoryOwner, latest.repositoryName, latest.repositoryUrl,
                    latest.createdAt, items.size(), history(latest));
        }).toList();

        var from = Math.min(page * size, cards.size());
        var to = Math.min(from + size, cards.size());
        return new SystemsPage(cards.subList(from, to), page, size, cards.size(), analyses.size(),
                analyses.stream().mapToInt(analysis -> analysis.findings.size()).sum(),
                analyses.stream().flatMap(analysis -> analysis.findings.stream())
                        .mapToInt(finding -> "Critical".equals(finding.severity) ? 1 : 0)
                        .sum(),
                analyses.stream().mapToInt(a -> a.filesAnalyzed).sum(),
                summarize(analyses.stream().flatMap(analysis -> {
                    var priorities = new ArrayList<String>();
                    analysis.findings.forEach(finding -> priorities.add(finding.severity));
                    analysis.suggestions.forEach(suggestion -> priorities.add(suggestion.severity));
                    return priorities.stream();
                }).toList()));
    }

    /**
     * Lista o histórico concluído de um repositório pertencente ao usuário.
     *
     * @param owner proprietário do repositório no GitHub
     * @param repository nome do repositório no GitHub
     * @param page índice da página solicitado
     * @param size quantidade solicitada de execuções por página
     * @param principal identidade autenticada
     * @return histórico paginado com execuções distintas
     */
    @GetMapping("/systems/{owner}/{repository}/history")
    @Transactional(readOnly = true)
    public HistoryPage history(@PathVariable String owner, @PathVariable String repository,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            Principal principal) {
        var user = users.findByEmail(principal.getName()).orElseThrow();
        page = Math.max(0, page);
        size = Math.min(20, Math.max(1, size));

        var all = distinctExecutions(repo.findCompletedHistory(user.id, owner, repository));
        var from = Math.min(page * size, all.size());
        var to = Math.min(from + size, all.size());
        return new HistoryPage(owner, repository,
                all.subList(from, to).stream().map(AnalysisController::history).toList(),
                page, size, all.size());
    }

    /**
     * Apresenta somente tarefas em andamento ou encerradas sem confirmação.
     *
     * @param principal identidade autenticada
     * @return tarefas visíveis para o usuário, com seus resumos atuais
     */
    @GetMapping("/tasks")
    @Transactional(readOnly = true)
    public List<Task> tasks(Principal principal) {
        var owner = users.findByEmail(principal.getName()).orElseThrow();
        return repo.findVisibleTasks(owner.id).stream()
                .map(analysis -> new Task(analysis.id, analysis.status, analysis.stage, analysis.repositoryUrl,
                        analysis.createdAt, analysis.semanticStatus, analysis.suggestionStatus,
                        summarize(analysis), analysis.taskAcknowledged))
                .toList();
    }

    /**
     * Confirma uma tarefa somente quando ela pertence ao usuário autenticado.
     *
     * @param id identificador da análise a confirmar
     * @param principal identidade autenticada
     * @return resposta sem conteúdo, inclusive quando a tarefa não pertence ao usuário
     */
    @PostMapping("/tasks/{id}/ack")
    @Transactional
    public ResponseEntity<Void> acknowledgeTask(@PathVariable UUID id, Principal principal) {
        var owner = users.findByEmail(principal.getName()).orElseThrow();
        repo.findByIdAndUserId(id, owner.id).ifPresent(analysis -> {
            analysis.taskAcknowledged = true;
            repo.save(analysis);
        });
        return ResponseEntity.noContent().build();
    }

    /**
     * Valida a origem e cria a análise e o evento de outbox na mesma transação.
     *
     * @param request URL pública e referência solicitadas
     * @param principal identidade autenticada
     * @return resposta de aceite com o endereço do resultado
     */
    @PostMapping
    @Transactional
    public ResponseEntity<Result> create(@Valid @RequestBody Request request, Principal principal) {
        var owner = users.findByEmail(principal.getName()).orElseThrow();
        log.atInfo().setMessage("analysis_started").addKeyValue("event", "analysis_started")
                .addKeyValue("userId", owner.id.toString()).log();

        var parts = GitHubClient.validate(request.repositoryUrl(), request.reference());

        var analysis = new Analysis();
        analysis.userId = owner.id;
        analysis.repositoryUrl = "https://github.com/" + parts[0] + "/" + parts[1];
        analysis.repositoryOwner = parts[0];
        analysis.repositoryName = parts[1];
        analysis.reference = request.reference() == null || request.reference().isBlank() ? null : request.reference();
        analysis.status = "PROCESSING";
        analysis.stage = "QUEUED";
        analysis.semanticStatus = "PENDING";
        analysis.suggestionStatus = "PENDING";
        analysis.taskAcknowledged = false;
        analysis.semanticModel = semantic.model();
        analysis.promptVersion = SemanticAnalysisService.PROMPT_VERSION + ":" + SemanticSuggestionService.PROMPT_VERSION;
        repo.save(analysis);
        if (outbox != null) {
            var event = new AnalysisOutboxEvent();
            event.analysisId = analysis.id;
            outbox.save(event);
        } else {
            Runnable submit = () -> jobs.submit(analysis.id, request.repositoryUrl(), request.reference());
            if (TransactionSynchronizationManager.isSynchronizationActive())
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() { submit.run(); }
                });
            else submit.run();
        }
        return ResponseEntity.accepted().header("Location", "/api/analyses/" + analysis.id).body(toResult(analysis));
    }

    /**
     * Obtém o resultado da análise pertencente ao usuário, usando cache só após a conclusão.
     *
     * @param id identificador da análise
     * @param principal identidade autenticada
     * @return estado atual ou resultado final da análise
     */
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public Result get(@PathVariable UUID id, Principal principal) {
        var owner = users.findByEmail(principal.getName()).orElseThrow();
        var cached = completedResults.get(owner.id, id);
        if (cached != null) return cached;
        var result = toResult(repo.findByIdAndUserId(id, owner.id).orElseThrow(NoSuchElementException::new));
        completedResults.put(owner.id, result);
        return result;
    }

    public static class Unprocessable extends RuntimeException {
        public Unprocessable() {
            super("O repositório não contém arquivos Java elegíveis");
        }
    }
}
