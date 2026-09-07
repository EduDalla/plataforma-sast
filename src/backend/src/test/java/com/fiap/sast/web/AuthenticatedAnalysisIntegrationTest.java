package com.fiap.sast.web;

import com.fiap.sast.auth.*;
import com.fiap.sast.github.GitHubClient;
import com.fiap.sast.persistence.*;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"sast.bootstrap.email=first@example.com", "sast.bootstrap.password=integration-test-password"})
@AutoConfigureMockMvc
class AuthenticatedAnalysisIntegrationTest {
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

    @BeforeEach void snapshot() throws Exception {
        var sample = Path.of("../../samples/VulnerableExample.java");
        if (!Files.exists(sample)) sample = Path.of("samples/VulnerableExample.java");
        when(github.download(any(), any())).thenReturn(new GitHubClient.Snapshot(
                "acme", "demo", "https://github.com/acme/demo", "main",
                List.of(new GitHubClient.File("samples/VulnerableExample.java", Files.readString(sample)))));
    }

    MockHttpSession login(String email, String password) throws Exception {
        var initial = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(initial.getResponse().getContentAsString(), "$.token");
        var anonymous = (MockHttpSession) initial.getRequest().getSession();
        String oldId = anonymous.getId();
        var result = mvc.perform(post("/api/auth/login").session(anonymous)
                .header("X-CSRF-TOKEN", token).contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email)).andReturn();
        var authenticated = (MockHttpSession) result.getRequest().getSession();
        assertNotEquals(oldId, authenticated.getId());
        mvc.perform(post("/api/analyses").session(authenticated).header("X-CSRF-TOKEN", token)
                .contentType("application/json").content("{\"repositoryUrl\":\"https://github.com/acme/demo\"}"))
                .andExpect(status().isForbidden());
        return authenticated;
    }

    @Test void bootstrapHashLoginSessionLogoutAndCsrf() throws Exception {
        var account = users.findByEmail("first@example.com").orElseThrow();
        assertNotEquals("integration-test-password", account.passwordHash);
        assertTrue(passwords.matches("integration-test-password", account.passwordHash));
        var bootstrap = new BootstrapUser(users, passwords, "first@example.com", "a-different-password");
        bootstrap.run(null);
        assertEquals(account.passwordHash, users.findByEmail(account.email).orElseThrow().passwordHash);
        mvc.perform(get("/api/auth/session")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/analyses/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/analyses").with(csrf()).contentType("application/json")
                .content("{\"repositoryUrl\":\"https://github.com/acme/demo\"}")).andExpect(status().isUnauthorized());
        for (String email : List.of("first@example.com", "unknown@example.com")) {
            mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json")
                    .content("{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos"));
        }
        var session = login("first@example.com", "integration-test-password");
        mvc.perform(get("/api/auth/session").session(session)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        assertTrue(session.isInvalid());
        mvc.perform(get("/api/auth/session").cookie(new jakarta.servlet.http.Cookie("JSESSIONID", "expired-session")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/health")).andExpect(status().isOk());
    }

    @Test void createsPersistsRetrievesThreeFindingsAndIsolatesOwners() throws Exception {
        var session = login("first@example.com", "integration-test-password");
        var created = mvc.perform(post("/api/analyses").session(session).with(csrf()).contentType("application/json")
                .content("{\"repositoryUrl\":\"https://github.com/acme/demo\",\"reference\":\"main\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.findings.length()").value(3))
                .andExpect(jsonPath("$.findings[0].cwe").value("CWE-798"))
                .andExpect(jsonPath("$.findings[1].cwe").value("CWE-78"))
                .andExpect(jsonPath("$.findings[2].cwe").value("CWE-502"))
                .andExpect(jsonPath("$.userId").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.archive").doesNotExist()).andExpect(jsonPath("$.sourceCode").doesNotExist())
                .andExpect(jsonPath("$.ast").doesNotExist()).andReturn();
        String body = created.getResponse().getContentAsString();
        String id = JsonPath.read(body, "$.analysisId");
        String location = "/api/analyses/" + id;
        assertEquals(location, created.getResponse().getHeader("Location"));
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM findings WHERE analysis_id = ?", Integer.class, UUID.fromString(id)));
        assertEquals(users.findByEmail("first@example.com").orElseThrow().id,
                analyses.findById(UUID.fromString(id)).orElseThrow().userId);
        mvc.perform(get(location).session(session)).andExpect(status().isOk()).andExpect(content().json(body));
        var second = users.findByEmail("second@example.com").orElseGet(() -> {
            var user = new AppUser(); user.email = "second@example.com";
            user.passwordHash = passwords.encode("second-test-password"); return users.save(user);
        });
        var other = login(second.email, "second-test-password");
        mvc.perform(get(location).session(other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/analyses/" + UUID.randomUUID()).session(session)).andExpect(status().isNotFound());
        var legacy = new Analysis(); legacy.repositoryUrl = "https://github.com/acme/legacy";
        legacy.repositoryOwner = "acme"; legacy.repositoryName = "legacy"; analyses.save(legacy);
        mvc.perform(get("/api/analyses/" + legacy.id).session(session)).andExpect(status().isNotFound());
        var columns = jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name IN ('analyses','findings')", String.class);
        assertFalse(columns.stream().anyMatch(c -> Set.of("archive", "source", "source_code", "ast", "content").contains(c)));
    }

    @Test void emptyFindingsAndErrors() throws Exception {
        var session = login("first@example.com", "integration-test-password");
        when(github.download(any(), any())).thenReturn(new GitHubClient.Snapshot("acme", "demo",
                "https://github.com/acme/demo", null, List.of(new GitHubClient.File("Safe.java", "class Safe {}"))));
        mvc.perform(post("/api/analyses").session(session).with(csrf()).contentType("application/json")
                .content("{\"repositoryUrl\":\"https://github.com/acme/demo\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.findings.length()").value(0));
        mvc.perform(post("/api/analyses").session(session).with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        var failures = List.of(new IllegalArgumentException(), new NoSuchElementException(),
                new GitHubClient.LimitException(), new AnalysisController.Unprocessable(),
                new GitHubClient.RateLimitException(), new GitHubClient.UnavailableException());
        int[] statuses = {400, 404, 413, 422, 429, 502};
        long before = analyses.count();
        for (int i = 0; i < failures.size(); i++) {
            reset(github); when(github.download(any(), any())).thenThrow(failures.get(i));
            mvc.perform(post("/api/analyses").session(session).with(csrf()).contentType("application/json")
                    .content("{\"repositoryUrl\":\"https://github.com/acme/demo\"}"))
                    .andExpect(status().is(statuses[i])).andExpect(jsonPath("$.detail").isString());
        }
        assertEquals(before, analyses.count());
    }
}
