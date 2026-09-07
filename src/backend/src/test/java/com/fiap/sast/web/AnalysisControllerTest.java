package com.fiap.sast.web;

import com.fiap.sast.analysis.SastEngine;
import com.fiap.sast.github.GitHubClient;
import com.fiap.sast.parsing.JavaParserSourceParser;
import com.fiap.sast.persistence.Analysis;
import com.fiap.sast.persistence.AnalysisRepository;
import com.fiap.sast.rules.DeserializationRule;
import com.fiap.sast.rules.HardcodedCredentialRule;
import com.fiap.sast.rules.RuntimeExecRule;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AnalysisControllerTest {
    @Test
    void javaInvalidoRetorna422ComArquivoLinhaEColuna() throws Exception {
        var github = mock(GitHubClient.class);
        when(github.download(eq("https://github.com/acme/demo"), eq("main")))
                .thenReturn(new GitHubClient.Snapshot("acme", "demo", "https://github.com/acme/demo", "main",
                        List.of(new GitHubClient.File("src/Broken.java", "class Broken {\n  void broken( {\n}"))));
        var repository = mock(AnalysisRepository.class);
        var mvc = mvc(github, repository);

        mvc.perform(post("/api/analyses")
                        .principal(() -> "test@example.com")
                        .contentType("application/json")
                        .content("{\"repositoryUrl\":\"https://github.com/acme/demo\",\"reference\":\"main\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.fileName").value("src/Broken.java"))
                .andExpect(jsonPath("$.line").isNumber())
                .andExpect(jsonPath("$.column").isNumber());

        verify(repository, never()).save(any(Analysis.class));
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
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.findings[0].ruleId").value("SAST-JAVA-001"))
                .andExpect(jsonPath("$.findings[0].severity").value("Critical"))
                .andExpect(jsonPath("$.findings[0].cwe").value("CWE-798"))
                .andExpect(jsonPath("$.findings[0].fileName").value("Example.java"))
                .andExpect(jsonPath("$.findings[0].line").isNumber())
                .andExpect(jsonPath("$.findings[0].column").isNumber())
                .andExpect(jsonPath("$.findings[0].snippet").isString());
    }

    private static MockMvc mvc(GitHubClient github, AnalysisRepository repository) {
        var engine = new SastEngine(new JavaParserSourceParser(), List.of(
                new HardcodedCredentialRule(), new RuntimeExecRule(), new DeserializationRule()));
        var users = mock(com.fiap.sast.auth.UserRepository.class);
        var user = new com.fiap.sast.auth.AppUser();
        user.email = "test@example.com";
        when(users.findByEmail(user.email)).thenReturn(java.util.Optional.of(user));
        var controller = new AnalysisController(github, engine, repository, users);
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }
}
