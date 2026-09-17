package com.fiap.sast.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.sast.analysis.*;
import com.fiap.sast.auth.UserRepository;
import com.fiap.sast.github.GitHubClient;
import com.fiap.sast.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.security.Principal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@RestController @RequestMapping("/api/analyses")
public class AnalysisController {
    private static final Logger log = LoggerFactory.getLogger(AnalysisController.class);
    private final GitHubClient git;
    private final SastEngine engine;
    private final AnalysisRepository repo;
    private final UserRepository users;
    private final ObjectMapper mapper;
    public AnalysisController(GitHubClient git, SastEngine engine, AnalysisRepository repo, UserRepository users,
            ObjectMapper mapper) {
        this.git = git; this.engine = engine; this.repo = repo; this.users = users; this.mapper = mapper;
    }
    public record Request(@NotBlank String repositoryUrl, String reference) {}
    public record Result(UUID analysisId, String status, String repositoryUrl, String reference,
            String language, int filesAnalyzed, Instant createdAt, List<SecurityFinding> findings) {}
    private Result toResult(Analysis analysis) {
        var findings = analysis.findings.stream().map(f -> new SecurityFinding(f.ruleId, f.title,
                f.severity, f.cwe, f.description, f.fileName, f.line, f.column, f.snippet, readTaintTrace(f.taintTrace)))
                .sorted(Comparator.comparing(SecurityFinding::fileName).thenComparingInt(SecurityFinding::line)
                        .thenComparingInt(SecurityFinding::column).thenComparing(SecurityFinding::ruleId)).toList();
        return new Result(analysis.id, analysis.status, analysis.repositoryUrl, analysis.reference,
                analysis.language, analysis.filesAnalyzed, analysis.createdAt, findings);
    }
    private TaintTrace readTaintTrace(String json) {
        if (json == null) return null;
        try {
            return mapper.readValue(json, TaintTrace.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("taintTrace persistido é inválido", e);
        }
    }
    private String writeTaintTrace(TaintTrace trace) {
        if (trace == null) return null;
        try {
            return mapper.writeValueAsString(trace);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
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
        return analyses.stream().filter(analysis -> known.add(analysis.repositoryOwner + "\u001d"
                + analysis.repositoryName + "\u001d" + String.valueOf(analysis.reference) + "\u001d"
                + findingsFingerprint(analysis))).toList();
    }

    @GetMapping("/systems") @Transactional(readOnly = true)
    public SystemsPage systems(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, Principal principal) {
        var owner = users.findByEmail(principal.getName()).orElseThrow();
        page = Math.max(0, page); size = Math.min(20, Math.max(1, size));
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
                analyses.stream().mapToInt(a -> a.findings.size()).sum(),
                analyses.stream().flatMap(a -> a.findings.stream()).mapToInt(f -> "Critical".equals(f.severity) ? 1 : 0).sum(),
                analyses.stream().mapToInt(a -> a.filesAnalyzed).sum());
    }

    @GetMapping("/systems/{owner}/{repository}/history") @Transactional(readOnly = true)
    public HistoryPage history(@PathVariable String owner, @PathVariable String repository,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            Principal principal) {
        var user = users.findByEmail(principal.getName()).orElseThrow();
        page = Math.max(0, page); size = Math.min(20, Math.max(1, size));
        var all = distinctExecutions(repo.findByUserIdOrderByCreatedAtDescIdDesc(user.id).stream()
                .filter(a -> owner.equals(a.repositoryOwner) && repository.equals(a.repositoryName)).toList());
        var from = Math.min(page * size, all.size());
        var to = Math.min(from + size, all.size());
        return new HistoryPage(owner, repository, all.subList(from, to).stream().map(AnalysisController::history).toList(),
                page, size, all.size());
    }
    @PostMapping @Transactional
    public ResponseEntity<Result> create(@Valid @RequestBody Request request, Principal principal) {
        var owner = users.findByEmail(principal.getName()).orElseThrow();
        log.atInfo().setMessage("analysis_started").addKeyValue("event", "analysis_started")
                .addKeyValue("userId", owner.id.toString()).log();
        var snapshot = git.download(request.repositoryUrl(), request.reference());
        if (snapshot.files().isEmpty()) throw new Unprocessable();
        var analysis = new Analysis(); analysis.userId = owner.id;
        analysis.repositoryUrl = snapshot.url(); analysis.repositoryOwner = snapshot.owner();
        analysis.repositoryName = snapshot.repo(); analysis.reference = snapshot.reference();
        analysis.filesAnalyzed = snapshot.files().size();
        snapshot.files().forEach(file -> engine.analyze(file.content(), file.path()).forEach(f -> {
            var finding = new Finding(); finding.analysis = analysis;
            finding.ruleId = f.ruleId(); finding.title = f.title(); finding.severity = f.severity();
            finding.cwe = f.cwe(); finding.description = f.description(); finding.fileName = f.fileName();
            finding.line = f.line(); finding.column = f.column(); finding.snippet = f.snippet();
            finding.taintTrace = writeTaintTrace(f.taintTrace());
            analysis.findings.add(finding);
        }));
        var previous = repo.findFirstByUserIdAndRepositoryOwnerAndRepositoryNameAndReferenceOrderByCreatedAtDescIdDesc(
                owner.id, analysis.repositoryOwner, analysis.repositoryName, analysis.reference);
        if (previous.isPresent() && findingsFingerprint(previous.get()).equals(findingsFingerprint(analysis))) {
            var reused = previous.get();
            reused.createdAt = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            repo.save(reused);
            log.atInfo().setMessage("analysis_reused")
                    .addKeyValue("event", "analysis_reused")
                    .addKeyValue("analysisId", reused.id.toString())
                    .addKeyValue("userId", owner.id.toString()).log();
            return ResponseEntity.ok(toResult(reused));
        }
        repo.save(analysis);
        var findingCount = analysis.findings.size();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {
                    log.atInfo().setMessage("analysis_completed")
                            .addKeyValue("event", "analysis_completed")
                            .addKeyValue("analysisId", analysis.id.toString())
                            .addKeyValue("userId", owner.id.toString())
                            .addKeyValue("filesAnalyzed", analysis.filesAnalyzed)
                            .addKeyValue("findings", findingCount).log();
                }
            });
        }
        return ResponseEntity.created(URI.create("/api/analyses/" + analysis.id)).body(toResult(analysis));
    }
    @GetMapping("/{id}") @Transactional(readOnly = true)
    public Result get(@PathVariable UUID id, Principal principal) {
        var owner = users.findByEmail(principal.getName()).orElseThrow();
        return toResult(repo.findByIdAndUserId(id, owner.id).orElseThrow(NoSuchElementException::new));
    }
    public static class Unprocessable extends RuntimeException {
        public Unprocessable() { super("O repositório não contém arquivos Java elegíveis"); }
    }
}
