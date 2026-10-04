package com.fiap.sast.web;

import com.fiap.sast.auth.AppUser;
import com.fiap.sast.auth.UserRepository;
import com.fiap.sast.analysis.AnalysisJobService;
import com.fiap.sast.analysis.SastEngine;
import com.fiap.sast.github.GitHubClient;
import com.fiap.sast.parsing.JavaParserSourceParser;
import com.fiap.sast.persistence.Analysis;
import com.fiap.sast.persistence.AnalysisRepository;
import com.fiap.sast.persistence.AiAssessmentEntity;
import com.fiap.sast.persistence.AiSuggestionEntity;
import com.fiap.sast.persistence.Finding;
import com.fiap.sast.rules.DeserializationRule;
import com.fiap.sast.rules.HardcodedCredentialRule;
import com.fiap.sast.rules.RuntimeExecRule;
import com.fiap.sast.semantic.SemanticAnalysisService;
import com.fiap.sast.semantic.SemanticSuggestionService;
import com.fiap.sast.semantic.AiAssessment;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnalysisResultSummaryTest {
    @Test
    void analysisHistoryAndSystemsCountBothKindsAndKeepUnclassifiedSuggestions() {
        var user = new AppUser();
        user.id = java.util.UUID.randomUUID();
        user.email = "summary@example.com";
        var analysis = new Analysis();
        analysis.userId = user.id;
        analysis.repositoryUrl = "https://github.com/acme/demo";
        analysis.repositoryOwner = "acme";
        analysis.repositoryName = "demo";
        analysis.reference = "main";
        analysis.status = "COMPLETED";
        analysis.semanticStatus = "COMPLETED";
        analysis.suggestionStatus = "COMPLETED";

        var finding = new Finding();
        finding.analysis = analysis;
        finding.ruleId = "SAST-JAVA-001";
        finding.title = "Credencial exposta";
        finding.severity = "Critical";
        finding.cwe = "CWE-798";
        finding.description = "Credencial no fonte";
        finding.fileName = "Config.java";
        finding.line = 5;
        finding.column = 2;
        finding.snippet = "password = \"x\"";
        finding.aiAssessment = AiAssessmentEntity.from(finding,
                new AiAssessment("llama3.2:3b", "2", 0.9, "Low", true, "Contexto protegido", "Sem ação"));
        analysis.findings.add(finding);

        suggestion(analysis, "High", "Possível N+1");
        suggestion(analysis, null, "Sugestão antiga");

        var analyses = mock(AnalysisRepository.class);
        when(analyses.findByIdAndUserId(analysis.id, user.id)).thenReturn(Optional.of(analysis));
        when(analyses.findByUserIdOrderByCreatedAtDescIdDesc(user.id)).thenReturn(List.of(analysis));
        var users = mock(UserRepository.class);
        when(users.findByEmail(user.email)).thenReturn(Optional.of(user));
        var controller = controller(analyses, users);
        Principal principal = user.email::toString;

        var result = controller.get(analysis.id, principal);
        assertSummary(result.resultSummary(), 3, 1, 1, 0, 0, 1, "Critical");
        assertNotNull(result.findings().get(0).aiAssessment());
        assertEquals("Critical", result.findings().get(0).severity(), "a avaliação da IA não altera a severidade da regra");
        assertEquals(result.resultSummary().total(), result.resultSummary().critical() + result.resultSummary().high()
                + result.resultSummary().medium() + result.resultSummary().low() + result.resultSummary().unclassified());

        var history = controller.history("acme", "demo", 0, 20, principal);
        assertSummary(history.history().get(0).resultSummary(), 3, 1, 1, 0, 0, 1, "Critical");

        var systems = controller.systems(0, 20, principal);
        assertSummary(systems.resultSummary(), 3, 1, 1, 0, 0, 1, "Critical");
        assertSummary(systems.systems().get(0).latest().resultSummary(), 3, 1, 1, 0, 0, 1, "Critical");
    }

    @Test
    void systemsSummaryIncludesAnalysesOutsideCurrentPage() {
        var user = new AppUser();
        user.id = java.util.UUID.randomUUID();
        user.email = "pages@example.com";
        var first = completedAnalysis(user.id, "first");
        suggestion(first, "High", "Suggestion");
        var second = completedAnalysis(user.id, "second");
        var finding = new Finding();
        finding.analysis = second;
        finding.ruleId = "RULE";
        finding.title = "Finding";
        finding.severity = "Critical";
        finding.fileName = "A.java";
        finding.line = 1;
        second.findings.add(finding);
        var analyses = mock(AnalysisRepository.class);
        when(analyses.findByUserIdOrderByCreatedAtDescIdDesc(user.id)).thenReturn(List.of(first, second));
        var users = mock(UserRepository.class);
        when(users.findByEmail(user.email)).thenReturn(Optional.of(user));

        var page = controller(analyses, users).systems(1, 1, user.email::toString);
        assertEquals(1, page.systems().size());
        assertEquals(2, page.totalSystems());
        assertEquals(2, page.totalAnalyses());
        assertSummary(page.resultSummary(), 2, 1, 1, 0, 0, 0, "Critical");
    }

    private static Analysis completedAnalysis(java.util.UUID userId, String repository) {
        var analysis = new Analysis();
        analysis.userId = userId;
        analysis.repositoryOwner = "acme";
        analysis.repositoryName = repository;
        analysis.repositoryUrl = "https://github.com/acme/" + repository;
        analysis.status = "COMPLETED";
        return analysis;
    }

    @Test
    void emptySummaryHasNoPriority() {
        var user = new AppUser();
        user.id = java.util.UUID.randomUUID();
        user.email = "empty@example.com";
        var analysis = new Analysis();
        analysis.userId = user.id;
        analysis.repositoryOwner = "acme";
        analysis.repositoryName = "empty";
        analysis.status = "COMPLETED";
        analysis.repositoryUrl = "https://github.com/acme/empty";
        var analyses = mock(AnalysisRepository.class);
        when(analyses.findByIdAndUserId(analysis.id, user.id)).thenReturn(Optional.of(analysis));
        var users = mock(UserRepository.class);
        when(users.findByEmail(user.email)).thenReturn(Optional.of(user));
        var result = controller(analyses, users).get(analysis.id, user.email::toString);
        assertSummary(result.resultSummary(), 0, 0, 0, 0, 0, 0, null);
    }

    private static AiSuggestionEntity suggestion(Analysis analysis, String severity, String title) {
        var suggestion = new AiSuggestionEntity();
        suggestion.analysis = analysis;
        suggestion.category = "PERFORMANCE";
        suggestion.severity = severity;
        suggestion.title = title;
        suggestion.fileName = "Config.java";
        suggestion.lineNumber = 6;
        suggestion.evidence = "repository.findById(id)";
        suggestion.rationale = "Consulta em laço";
        suggestion.confidence = 0.8;
        suggestion.recommendation = "Avaliar busca em lote";
        suggestion.limitations = "Confirmar em execução";
        suggestion.model = "llama3.2:3b";
        suggestion.promptVersion = "3";
        analysis.suggestions.add(suggestion);
        return suggestion;
    }

    private static AnalysisController controller(AnalysisRepository analyses, UserRepository users) {
        var parser = new JavaParserSourceParser();
        return new AnalysisController(mock(GitHubClient.class),
                new SastEngine(parser, List.of(new HardcodedCredentialRule(), new RuntimeExecRule(), new DeserializationRule())),
                analyses, users, new ObjectMapper(), mock(SemanticAnalysisService.class),
                mock(SemanticSuggestionService.class), mock(AnalysisJobService.class));
    }

    private static void assertSummary(AnalysisController.ResultSummary summary, int total,
            int critical, int high, int medium, int low, int unclassified, String highest) {
        assertEquals(total, summary.total());
        assertEquals(critical, summary.critical());
        assertEquals(high, summary.high());
        assertEquals(medium, summary.medium());
        assertEquals(low, summary.low());
        assertEquals(unclassified, summary.unclassified());
        assertEquals(highest, summary.highestPriority());
    }
}
