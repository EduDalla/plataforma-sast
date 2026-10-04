package com.fiap.sast.web;

import com.fiap.sast.analysis.SastEngine;
import com.fiap.sast.github.GitHubClient;
import com.fiap.sast.parsing.JavaParserSourceParser;
import com.fiap.sast.persistence.Analysis;
import com.fiap.sast.persistence.AnalysisRepository;
import com.fiap.sast.persistence.AnalysisOutboxRepository;
import com.fiap.sast.rules.DeserializationRule;
import com.fiap.sast.rules.HardcodedCredentialRule;
import com.fiap.sast.rules.RuntimeExecRule;
import com.fiap.sast.semantic.AiAssessment;
import com.fiap.sast.semantic.SemanticAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AnalysisControllerTest {
    @Test
    void criaJobMesmoAntesDoParser() throws Exception {
        var github = mock(GitHubClient.class);
        var repository = mock(AnalysisRepository.class);
        when(repository.save(any(Analysis.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var mvc = mvc(github, repository);

        mvc.perform(post("/api/analyses")
                        .principal(() -> "test@example.com")
                        .contentType("application/json")
                        .content("{\"repositoryUrl\":\"https://github.com/acme/demo\",\"reference\":\"main\"}"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/analyses/.+")))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.stage").value("QUEUED"));
        verify(repository).save(any(Analysis.class));
    }

    @Test
    void gravaEventoOutboxNaMesmaCriacaoEDeixaPublicacaoParaDepoisDoCommit() throws Exception {
        var github = mock(GitHubClient.class);
        var repository = mock(AnalysisRepository.class);
        var outbox = mock(AnalysisOutboxRepository.class);
        when(repository.save(any(Analysis.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var users = mock(com.fiap.sast.auth.UserRepository.class);
        var user = new com.fiap.sast.auth.AppUser(); user.id = UUID.randomUUID(); user.email = "test@example.com";
        when(users.findByEmail(user.email)).thenReturn(Optional.of(user));
        var jobs = mock(com.fiap.sast.analysis.AnalysisJobService.class);
        var controller = new AnalysisController(github, mock(SastEngine.class), repository, users,
                new tools.jackson.databind.ObjectMapper(), mock(SemanticAnalysisService.class),
                mock(com.fiap.sast.semantic.SemanticSuggestionService.class), jobs, outbox);

        controller.create(new AnalysisController.Request("https://github.com/acme/demo", "main"), () -> user.email);

        verify(outbox).save(any(com.fiap.sast.persistence.AnalysisOutboxEvent.class));
        verifyNoInteractions(jobs);
    }

    @Test
    void respostaIncluiTodosOsCamposDoFinding() throws Exception {
        var github = mock(GitHubClient.class);
        when(github.download(any(), any())).thenReturn(new GitHubClient.Snapshot(
                "acme", "demo", "https://github.com/acme/demo", "main",
                List.of(new GitHubClient.File("Example.java", "class Example { String password = \"x\"; }"))));
        var repository = mock(AnalysisRepository.class);
        when(repository.save(any(Analysis.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var mvc = mvc(github, repository);

        mvc.perform(post("/api/analyses")
                        .principal(() -> "test@example.com")
                        .contentType("application/json")
                        .content("{\"repositoryUrl\":\"https://github.com/acme/demo\",\"reference\":\"main\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.findings").isArray())
                .andExpect(jsonPath("$.findings").isEmpty())
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    void cadaSolicitacaoCriaJobProprio() throws Exception {
        var github = mock(GitHubClient.class);
        when(github.download(any(), any())).thenReturn(new GitHubClient.Snapshot(
                "acme", "demo", "https://github.com/acme/demo", "main",
                List.of(new GitHubClient.File("Example.java", "class Example { String password = \"x\"; }"))));
        var repository = mock(AnalysisRepository.class);
        var saved = new AtomicReference<Analysis>();
        when(repository.save(any(Analysis.class))).thenAnswer(invocation -> {
            saved.set(invocation.getArgument(0));
            return saved.get();
        });
        var mvc = mvc(github, repository);

        var first = mvc.perform(post("/api/analyses").principal(() -> "test@example.com")
                        .contentType("application/json")
                        .content("{\"repositoryUrl\":\"https://github.com/acme/demo\",\"reference\":\"main\"}"))
                .andExpect(status().isAccepted()).andReturn();
        var second = mvc.perform(post("/api/analyses").principal(() -> "test@example.com")
                        .contentType("application/json")
                        .content("{\"repositoryUrl\":\"https://github.com/acme/demo\",\"reference\":\"main\"}"))
                .andExpect(status().isAccepted()).andReturn();

        var idPattern = ".*\\\"analysisId\\\":\\\"([^\\\"]+).*";
        org.junit.jupiter.api.Assertions.assertNotEquals(first.getResponse().getContentAsString().replaceAll(idPattern, "$1"),
                second.getResponse().getContentAsString().replaceAll(idPattern, "$1"));
    }

    @Test
    void tasksFicamIsoladasEConfirmacaoMarcaApenasAExecucaoDoUsuario() {
        var repository = mock(AnalysisRepository.class);
        var github = mock(GitHubClient.class);
        var userRepository = mock(com.fiap.sast.auth.UserRepository.class);
        var user = new com.fiap.sast.auth.AppUser();
        user.id = UUID.randomUUID();
        user.email = "test@example.com";
        when(userRepository.findByEmail(user.email)).thenReturn(Optional.of(user));
        var analysis = new Analysis();
        analysis.userId = user.id;
        analysis.status = "COMPLETED";
        analysis.taskAcknowledged = false;
        when(repository.findByUserIdOrderByCreatedAtDescIdDesc(user.id)).thenReturn(List.of(analysis));
        when(repository.findByIdAndUserId(analysis.id, user.id)).thenReturn(Optional.of(analysis));
        var controller = new AnalysisController(github, mock(SastEngine.class), repository, userRepository,
                new tools.jackson.databind.ObjectMapper(), mock(SemanticAnalysisService.class),
                mock(com.fiap.sast.semantic.SemanticSuggestionService.class), mock(com.fiap.sast.analysis.AnalysisJobService.class));

        var tasks = controller.tasks(() -> user.email);
        assertEquals(1, tasks.size());
        assertEquals(analysis.id, tasks.getFirst().analysisId());
        controller.acknowledgeTask(analysis.id, () -> user.email);
        assertEquals(true, analysis.taskAcknowledged);
        verify(repository).save(analysis);
    }

    private static MockMvc mvc(GitHubClient github, AnalysisRepository repository) {
        var engine = new SastEngine(new JavaParserSourceParser(), List.of(
                new HardcodedCredentialRule(), new RuntimeExecRule(), new DeserializationRule()));
        var users = mock(com.fiap.sast.auth.UserRepository.class);
        var user = new com.fiap.sast.auth.AppUser();
        user.email = "test@example.com";
        when(users.findByEmail(user.email)).thenReturn(java.util.Optional.of(user));
        var semantic = mock(SemanticAnalysisService.class);
        when(semantic.model()).thenReturn("llama3.2:3b");
        when(semantic.enrich(any())).thenAnswer(invocation -> {
            List<SemanticAnalysisService.Candidate> candidates = invocation.getArgument(0);
            var values = new java.util.HashMap<java.util.UUID, AiAssessment>();
            for (var candidate : candidates) values.put(candidate.findingId(),
                    new AiAssessment("llama3.2:3b", "1", 0.8, "High", false, "Risco", "Corrigir"));
            return new SemanticAnalysisService.Result(candidates.isEmpty() ? "NOT_APPLICABLE" : "COMPLETED", values);
        });
        var suggestionService = mock(com.fiap.sast.semantic.SemanticSuggestionService.class);
        when(suggestionService.scan(any())).thenReturn(
                new com.fiap.sast.semantic.SemanticSuggestionService.Result("NOT_APPLICABLE", List.of()));
        var jobs = mock(com.fiap.sast.analysis.AnalysisJobService.class);
        var controller = new AnalysisController(github, engine, repository, users,
                new tools.jackson.databind.ObjectMapper(), semantic, suggestionService, jobs);
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }
}
