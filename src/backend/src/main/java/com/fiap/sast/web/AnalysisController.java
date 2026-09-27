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
import com.fiap.sast.persistence.Finding;
import com.fiap.sast.semantic.AiAssessment;
import com.fiap.sast.semantic.AiSuggestion;
import com.fiap.sast.semantic.SemanticAnalysisService;
import com.fiap.sast.semantic.SemanticSuggestionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

    public AnalysisController(GitHubClient git, SastEngine engine, AnalysisRepository repo, UserRepository users,
            ObjectMapper mapper, SemanticAnalysisService semantic, SemanticSuggestionService suggestionService,
            AnalysisJobService jobs) {
        this.git = git;
        this.engine = engine;
        this.repo = repo;
        this.users = users;
        this.mapper = mapper;
        this.semantic = semantic;
        this.suggestionService = suggestionService;
        this.jobs = jobs;
    }

    public record Request(@NotBlank String repositoryUrl, String reference) {}

    public record FindingResult(String ruleId, String title, String severity, String cwe,
            String description, String fileName, int line, int column, String snippet,
            TaintTrace taintTrace, AiAssessment aiAssessment) {}

    public record Result(UUID analysisId, String status, String repositoryUrl, String reference,
            String language, int filesAnalyzed, Instant createdAt, String semanticStatus,
            String suggestionStatus, List<AiSuggestion> suggestions, List<FindingResult> findings,
            String stage, int filesProcessed, int filesTotal, String failureStage, String failureMessage) {}

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
                analysis.suggestionStatus, suggestions, findings, analysis.stage, analysis.filesProcessed,
                analysis.filesTotal, analysis.failureStage, analysis.failureMessage);
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

    public record HistoryEntry(UUID analysisId, String reference, Instant createdAt, int filesAnalyzed, int findings) {}
    public record SystemCard(String owner, String repositoryName, String repositoryUrl,
            Instant latestCreatedAt, int totalAnalyses, HistoryEntry latest) {}
    public record SystemsPage(List<SystemCard> systems, int page, int size, int totalSystems,
            int totalAnalyses, int totalFindings, int totalCritical, int totalFiles) {}
    public record HistoryPage(String owner, String repositoryName, List<HistoryEntry> history,
            int page, int size, int total) {}

    private static HistoryEntry history(Analysis a) {
        return new HistoryEntry(a.id, a.reference, a.createdAt, a.filesAnalyzed, a.findings.size());
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
                + analysis.snapshotHash + "\u001d" + analysis.suggestionStatus)).toList();
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

    @GetMapping("/systems")
    @Transactional(readOnly = true)
    public SystemsPage systems(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, Principal principal) {
        var owner = users.findByEmail(principal.getName()).orElseThrow();
        page = Math.max(0, page);
        size = Math.min(20, Math.max(1, size));

        var analyses = distinctExecutions(repo.findByUserIdOrderByCreatedAtDescIdDesc(owner.id));
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
                analyses.stream().mapToInt(a -> a.filesAnalyzed).sum());
    }

    @GetMapping("/systems/{owner}/{repository}/history")
    @Transactional(readOnly = true)
    public HistoryPage history(@PathVariable String owner, @PathVariable String repository,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            Principal principal) {
        var user = users.findByEmail(principal.getName()).orElseThrow();
        page = Math.max(0, page);
        size = Math.min(20, Math.max(1, size));

        var all = distinctExecutions(repo.findByUserIdOrderByCreatedAtDescIdDesc(user.id).stream()
                .filter(a -> owner.equals(a.repositoryOwner) && repository.equals(a.repositoryName)).toList());
        var from = Math.min(page * size, all.size());
        var to = Math.min(from + size, all.size());
        return new HistoryPage(owner, repository,
                all.subList(from, to).stream().map(AnalysisController::history).toList(),
                page, size, all.size());
    }

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
        analysis.semanticModel = semantic.model();
        analysis.promptVersion = SemanticAnalysisService.PROMPT_VERSION + ":" + SemanticSuggestionService.PROMPT_VERSION;
        repo.save(analysis);
        Runnable submit = () -> jobs.submit(analysis.id, request.repositoryUrl(), request.reference());
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { submit.run(); }
            });
        else submit.run();
        return ResponseEntity.accepted().header("Location", "/api/analyses/" + analysis.id).body(toResult(analysis));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public Result get(@PathVariable UUID id, Principal principal) {
        var owner = users.findByEmail(principal.getName()).orElseThrow();
        return toResult(repo.findByIdAndUserId(id, owner.id).orElseThrow(NoSuchElementException::new));
    }

    public static class Unprocessable extends RuntimeException {
        public Unprocessable() {
            super("O repositório não contém arquivos Java elegíveis");
        }
    }
}
