package com.fiap.sast.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fiap.sast.auth.AppUser;
import com.fiap.sast.auth.UserRepository;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
        "sast.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "sast.bootstrap.email=bootstrap@example.com",
        "sast.bootstrap.password=repository-test-password",
        "sast.rabbit.publisher-delay-ms=600000",
        "logging.level.root=ERROR"
})
class AnalysisReadRepositoryIntegrationTest {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @Autowired private UserRepository users;
    @Autowired private AnalysisRepository analyses;
    @Autowired private JdbcTemplate jdbc;

    @Test
    @Transactional
    void filtraConsultasPorUsuarioEstadoERepositorioComIndicesMigrados() {
        var owner = user("owner@example.com");
        var stranger = user("stranger@example.com");
        var completed = analysis(owner.id, "demo", "COMPLETED", true);
        var legacy = analysis(owner.id, "other", "Completed", true);
        var processing = analysis(owner.id, "demo", "PROCESSING", false);
        var failed = analysis(owner.id, "demo", "FAILED", false);
        analysis(stranger.id, "demo", "COMPLETED", false);

        assertEquals(Set.of(completed.id, legacy.id), analyses.findCompletedByUser(owner.id).stream()
                .map(value -> value.id).collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of(completed.id), analyses.findCompletedHistory(owner.id, "acme", "demo")
                .stream().map(value -> value.id).collect(java.util.stream.Collectors.toSet()));
        assertEquals(2, analyses.findVisibleTasks(owner.id).size());
        assertTrue(analyses.findVisibleTasks(owner.id).stream()
                .allMatch(value -> value.id.equals(processing.id) || value.id.equals(failed.id)));
        assertTrue(analyses.findCompletedByUser(stranger.id).stream()
                .noneMatch(value -> value.id.equals(completed.id)));

        var indexes = jdbc.queryForList("select indexname from pg_indexes where tablename = 'analyses'",
                String.class);
        assertTrue(indexes.contains("analyses_user_status_recent_idx"));
        assertTrue(indexes.contains("analyses_user_repository_status_recent_idx"));
    }

    private AppUser user(String email) {
        var user = new AppUser();
        user.email = email;
        user.passwordHash = "unused-in-repository-test";
        return users.saveAndFlush(user);
    }

    private Analysis analysis(UUID userId, String repository, String status, boolean acknowledged) {
        var analysis = new Analysis();
        analysis.userId = userId;
        analysis.repositoryOwner = "acme";
        analysis.repositoryName = repository;
        analysis.repositoryUrl = "https://github.com/acme/" + repository;
        analysis.status = status;
        analysis.taskAcknowledged = acknowledged;
        return analyses.saveAndFlush(analysis);
    }
}
