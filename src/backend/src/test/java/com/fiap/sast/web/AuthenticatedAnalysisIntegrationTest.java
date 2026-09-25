package com.fiap.sast.web;

import com.fiap.sast.auth.*;
import com.fiap.sast.github.GitHubClient;
import com.fiap.sast.persistence.*;
import com.fiap.sast.semantic.OllamaGateway;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"sast.bootstrap.email=first@example.com", "sast.bootstrap.password=integration-test-password"})
@AutoConfigureMockMvc
class AuthenticatedAnalysisIntegrationTest {
    private static final String VULNERABLE_SOURCE = "import java.io.InputStream;\n"
            + "import java.io.ObjectInputStream;\n"
            + "public final class InMemoryVulnerable {\n"
            + "  private static final String password = \"123456\";\n"
            + "  public Object execute(String userInput, InputStream stream) throws Exception {\n"
            + "    Runtime.getRuntime().exec(userInput);\n"
            + "    ObjectInputStream input = new ObjectInputStream(stream);\n"
            + "    return input.readObject();\n"
            + "  }\n"
            + "}";

    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        postgres.start();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    @AfterAll static void stop() { postgres.stop(); }
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AnalysisRepository analyses;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean GitHubClient github;
    @MockitoBean OllamaGateway ollama;

    @BeforeEach void snapshot() throws Exception {
        when(ollama.generate(any(), any())).thenReturn("{\"confidence\":0.8,\"suggestedSeverity\":\"High\",\"likelyFalsePositive\":false,\"rationale\":\"Risco contextual\",\"remediation\":\"Use entrada validada.\"}");
        when(github.download(any(), any())).thenReturn(new GitHubClient.Snapshot(
                "acme", "demo", "https://github.com/acme/demo", "main",
                List.of(new GitHubClient.File("InMemoryVulnerable.java", VULNERABLE_SOURCE))));
    }

    String login(String email, String password) throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email)).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    @Test void bootstrapHashLoginAndBearer() throws Exception {
        var account = users.findByEmail("first@example.com").orElseThrow();
        assertNotEquals("integration-test-password", account.passwordHash);
        assertTrue(passwords.matches("integration-test-password", account.passwordHash));
        var bootstrap = new BootstrapUser(users, passwords, "first@example.com", "a-different-password");
        bootstrap.run(null);
        assertEquals(account.passwordHash, users.findByEmail(account.email).orElseThrow().passwordHash);
        mvc.perform(get("/api/auth/session")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/analyses/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/analyses").contentType("application/json")
                .content("{\"repositoryUrl\":\"https://github.com/acme/demo\"}")).andExpect(status().isUnauthorized());
        for (String email : List.of("first@example.com", "unknown@example.com")) {
            mvc.perform(post("/api/auth/login").contentType("application/json")
                    .content("{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos"));
        }
        var token = login("first@example.com", "integration-test-password");
        mvc.perform(get("/api/auth/session").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/session").header("Authorization", "Bearer inválido"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/health")).andExpect(status().isOk());
    }

    @Test void registersUserWithBcryptAndRejectsDuplicateEmail() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content("{\"email\":\"New@Example.com\",\"password\":\"strong-password\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
        var account = users.findByEmail("new@example.com").orElseThrow();
        assertNotEquals("strong-password", account.passwordHash);
        assertTrue(passwords.matches("strong-password", account.passwordHash));
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content("{\"email\":\"new@example.com\",\"password\":\"another-password\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value("Este e-mail já está cadastrado"));
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content("{\"email\":\"short@example.com\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test void createsPersistsRetrievesThreeFindingsAndIsolatesOwners() throws Exception {
        var token = login("first@example.com", "integration-test-password");
        var created = mvc.perform(post("/api/analyses").header("Authorization", "Bearer " + token).contentType("application/json")
                .content("{\"repositoryUrl\":\"https://github.com/acme/demo\",\"reference\":\"main\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.findings.length()").value(3))
                .andExpect(jsonPath("$.findings[0].cwe").value("CWE-798"))
                .andExpect(jsonPath("$.findings[1].cwe").value("CWE-78"))
                .andExpect(jsonPath("$.findings[2].cwe").value("CWE-502"))
                .andExpect(jsonPath("$.semanticStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.findings[0].aiAssessment.model").value("llama3.2:3b"))
                .andExpect(jsonPath("$.userId").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.archive").doesNotExist()).andExpect(jsonPath("$.sourceCode").doesNotExist())
                .andExpect(jsonPath("$.ast").doesNotExist()).andReturn();
        String body = created.getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.analysisId");
        String location = "/api/analyses/" + id;
        assertEquals(location, created.getResponse().getHeader("Location"));
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM findings WHERE analysis_id = ?", Integer.class, UUID.fromString(id)));
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM ai_assessments WHERE finding_id IN (SELECT id FROM findings WHERE analysis_id = ?)", Integer.class, UUID.fromString(id)));
        assertEquals(users.findByEmail("first@example.com").orElseThrow().id,
                analyses.findById(UUID.fromString(id)).orElseThrow().userId);
        mvc.perform(get(location).header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andExpect(content().json(body));
        var second = users.findByEmail("second@example.com").orElseGet(() -> {
            var user = new AppUser(); user.email = "second@example.com";
            user.passwordHash = passwords.encode("second-test-password"); return users.save(user);
        });
        var other = login(second.email, "second-test-password");
        mvc.perform(get(location).header("Authorization", "Bearer " + other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/analyses/" + UUID.randomUUID()).header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
        var legacy = new Analysis(); legacy.repositoryUrl = "https://github.com/acme/legacy";
        legacy.repositoryOwner = "acme"; legacy.repositoryName = "legacy"; analyses.save(legacy);
        mvc.perform(get("/api/analyses/" + legacy.id).header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
        var columns = jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name IN ('analyses','findings')", String.class);
        assertFalse(columns.stream().anyMatch(c -> Set.of("archive", "source", "source_code", "ast", "content").contains(c)));
    }

    @Test void emptyFindingsAndErrors() throws Exception {
        var token = login("first@example.com", "integration-test-password");
        when(github.download(any(), any())).thenReturn(new GitHubClient.Snapshot("acme", "demo",
                "https://github.com/acme/demo", null, List.of(new GitHubClient.File("Safe.java", "class Safe {}"))));
        mvc.perform(post("/api/analyses").header("Authorization", "Bearer " + token).contentType("application/json")
                .content("{\"repositoryUrl\":\"https://github.com/acme/demo\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.findings.length()").value(0))
                .andExpect(jsonPath("$.semanticStatus").value("NOT_APPLICABLE"));
        mvc.perform(post("/api/analyses").header("Authorization", "Bearer " + token).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        var failures = List.of(new IllegalArgumentException(), new NoSuchElementException(),
                new GitHubClient.LimitException(), new AnalysisController.Unprocessable(),
                new GitHubClient.RateLimitException(), new GitHubClient.UnavailableException());
        int[] statuses = {400, 404, 413, 422, 429, 502};
        long before = analyses.count();
        for (int i = 0; i < failures.size(); i++) {
            reset(github); when(github.download(any(), any())).thenThrow(failures.get(i));
            mvc.perform(post("/api/analyses").header("Authorization", "Bearer " + token).contentType("application/json")
                    .content("{\"repositoryUrl\":\"https://github.com/acme/demo\"}"))
                    .andExpect(status().is(statuses[i])).andExpect(jsonPath("$.detail").isString());
        }
        assertEquals(before, analyses.count());
    }

    @Test void taintTraceRoundTripsThroughJsonAndDatabase() throws Exception {
        var token = login("first@example.com", "integration-test-password");
        when(github.download(any(), any())).thenReturn(new GitHubClient.Snapshot("acme", "taint",
                "https://github.com/acme/taint", "main", List.of(new GitHubClient.File("Controller.java",
                "class Controller {\n"
                        + "  void run(@org.springframework.web.bind.annotation.RequestParam String cmd) throws Exception {\n"
                        + "    Runtime.getRuntime().exec(cmd);\n"
                        + "  }\n"
                        + "}"))));

        var created = mvc.perform(post("/api/analyses").header("Authorization", "Bearer " + token).contentType("application/json")
                        .content("{\"repositoryUrl\":\"https://github.com/acme/taint\",\"reference\":\"main\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.findings[1].ruleId").value("TAINT-CMDI-001"))
                .andExpect(jsonPath("$.findings[1].taintTrace.source.kind").value("http_param"))
                .andExpect(jsonPath("$.findings[1].taintTrace.sink.kind").value("sink"))
                .andExpect(jsonPath("$.findings[1].aiAssessment.rationale").value("Risco contextual"))
                .andReturn();
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.analysisId");

        assertNotNull(jdbc.queryForObject("SELECT taint_trace FROM findings WHERE analysis_id = ? AND taint_trace IS NOT NULL",
                String.class, UUID.fromString(id)));
        mvc.perform(get("/api/analyses/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.findings[1].taintTrace.source.kind").value("http_param"));
    }

    @Test void reusesAnalysisWhenSecurityFindingsDoNotChange() throws Exception {
        var token = login("first@example.com", "integration-test-password");
        when(github.download(any(), any())).thenReturn(new GitHubClient.Snapshot("acme", "dedupe",
                "https://github.com/acme/dedupe", "main", List.of(new GitHubClient.File("Example.java",
                "class Example { String password = \"x\"; }"))));
        var first = mvc.perform(post("/api/analyses").header("Authorization", "Bearer " + token).contentType("application/json")
                        .content("{\"repositoryUrl\":\"https://github.com/acme/dedupe\",\"reference\":\"main\"}"))
                .andExpect(status().isCreated()).andReturn();
        var firstId = JsonPath.read(first.getResponse().getContentAsString(), "$.analysisId").toString();

        var repeated = mvc.perform(post("/api/analyses").header("Authorization", "Bearer " + token).contentType("application/json")
                        .content("{\"repositoryUrl\":\"https://github.com/acme/dedupe\",\"reference\":\"main\"}"))
                .andExpect(status().isOk()).andReturn();
        assertEquals(firstId, JsonPath.read(repeated.getResponse().getContentAsString(), "$.analysisId").toString());
        assertEquals(1, analyses.findByUserIdOrderByCreatedAtDescIdDesc(
                users.findByEmail("first@example.com").orElseThrow().id).stream()
                .filter(analysis -> "dedupe".equals(analysis.repositoryName)).count());
    }

    @Test void degradedExecutionCanBeReassessedWithoutLosingFindings() throws Exception {
        var token = login("first@example.com", "integration-test-password");
        when(github.download(any(), any())).thenReturn(new GitHubClient.Snapshot("acme", "retry",
                "https://github.com/acme/retry", "main", List.of(new GitHubClient.File("Example.java",
                "class Example { String password = \"x\"; }"))));
        when(ollama.generate(any(), any())).thenThrow(new OllamaGateway.OllamaFailure(false));
        var degraded = mvc.perform(post("/api/analyses").header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"repositoryUrl\":\"https://github.com/acme/retry\",\"reference\":\"main\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.semanticStatus").value("DEGRADED"))
                .andExpect(jsonPath("$.findings.length()").value(1))
                .andExpect(jsonPath("$.findings[0].aiAssessment").isEmpty()).andReturn();
        doReturn("{\"confidence\":0.8,\"suggestedSeverity\":\"High\",\"likelyFalsePositive\":false,\"rationale\":\"Risco\",\"remediation\":\"Corrija\"}")
                .when(ollama).generate(any(), any());
        var completed = mvc.perform(post("/api/analyses").header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"repositoryUrl\":\"https://github.com/acme/retry\",\"reference\":\"main\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.semanticStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.findings[0].aiAssessment.model").value("llama3.2:3b"))
                .andReturn();
        assertNotEquals(JsonPath.read(degraded.getResponse().getContentAsString(), "$.analysisId").toString(),
                JsonPath.read(completed.getResponse().getContentAsString(), "$.analysisId").toString());
    }
}
