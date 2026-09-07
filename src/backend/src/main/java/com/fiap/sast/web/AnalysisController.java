package com.fiap.sast.web;

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

@RestController @RequestMapping("/api/analyses")
public class AnalysisController {
    private final GitHubClient git;
    private final SastEngine engine;
    private final AnalysisRepository repo;
    private final UserRepository users;
    public AnalysisController(GitHubClient git, SastEngine engine, AnalysisRepository repo, UserRepository users) {
        this.git = git; this.engine = engine; this.repo = repo; this.users = users;
    }
    public record Request(@NotBlank String repositoryUrl, String reference) {}
    public record Result(UUID analysisId, String status, String repositoryUrl, String reference,
            String language, int filesAnalyzed, Instant createdAt, List<SecurityFinding> findings) {
        static Result from(Analysis analysis) {
            var findings = analysis.findings.stream().map(f -> new SecurityFinding(f.ruleId, f.title,
                    f.severity, f.cwe, f.description, f.fileName, f.line, f.column, f.snippet))
                    .sorted(Comparator.comparing(SecurityFinding::fileName).thenComparingInt(SecurityFinding::line)
                            .thenComparingInt(SecurityFinding::column).thenComparing(SecurityFinding::ruleId)).toList();
            return new Result(analysis.id, analysis.status, analysis.repositoryUrl, analysis.reference,
                    analysis.language, analysis.filesAnalyzed, analysis.createdAt, findings);
        }
    }
    @PostMapping @Transactional
    public ResponseEntity<Result> create(@Valid @RequestBody Request request, Principal principal) {
        var owner = users.findByEmail(principal.getName()).orElseThrow();
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
            analysis.findings.add(finding);
        }));
        repo.save(analysis);
        return ResponseEntity.created(URI.create("/api/analyses/" + analysis.id)).body(Result.from(analysis));
    }
    @GetMapping("/{id}") @Transactional(readOnly = true)
    public Result get(@PathVariable UUID id, Principal principal) {
        var owner = users.findByEmail(principal.getName()).orElseThrow();
        return Result.from(repo.findByIdAndUserId(id, owner.id).orElseThrow(NoSuchElementException::new));
    }
    public static class Unprocessable extends RuntimeException {
        public Unprocessable() { super("O repositório não contém arquivos Java elegíveis"); }
    }
}
