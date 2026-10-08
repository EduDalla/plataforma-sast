package com.fiap.sast.messaging;

import com.fiap.sast.analysis.AnalysisJobService;
import com.fiap.sast.github.GitHubClient;
import com.fiap.sast.persistence.AnalysisOutboxRepository;
import com.fiap.sast.persistence.AnalysisRepository;
import com.fiap.sast.semantic.OllamaGateway;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** O-01: banco e broker reais isolados; snapshots e falhas consultivas controlados em memória. */
@SpringBootTest(properties = {
        "sast.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "sast.bootstrap.email=bootstrap@operation.test",
        "sast.bootstrap.password=operation-bootstrap-password",
        "sast.rabbit.publisher-delay-ms=600000",
        "sast.rabbit.reconciler-delay-ms=600000",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "logging.level.root=ERROR"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(OperationIntegrationTest.WorkerConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class OperationIntegrationTest {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");
    private static final GenericContainer<?> BROKER = new GenericContainer<>("rabbitmq:4-management-alpine")
            .withExposedPorts(5672)
            .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1));
    private static final String SHA = "a".repeat(40);
    private static final String PASSWORD = "operation-password-sentinel";
    private static final String SOURCE = "import java.io.*; class Sample {\n"
            + "String password=\"source-secret-sentinel\";\n"
            + "Object run(String cmd, InputStream stream) throws Exception {\n"
            + "Runtime.getRuntime().exec(cmd);\n"
            + "ObjectInputStream in=new ObjectInputStream(stream); return in.readObject(); } }";

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        BROKER.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.rabbitmq.host", BROKER::getHost);
        registry.add("spring.rabbitmq.port", () -> BROKER.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");
    }

    @AfterAll
    static void stopServices(@Autowired RabbitListenerEndpointRegistry listeners) {
        listeners.stop();
        BROKER.stop();
        POSTGRES.stop();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class WorkerConfiguration {
        @Bean AnalysisWorkerListener listener(AnalysisJobService jobs) {
            return new AnalysisWorkerListener(jobs);
        }
        @Bean AnalysisLeaseReconciler reconciler(AnalysisRepository analyses, AnalysisOutboxRepository events) {
            return new AnalysisLeaseReconciler(analyses, events);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AnalysisOutboxPublisher publisher;
    @Autowired AnalysisLeaseReconciler reconciler;
    @Autowired RabbitTemplate rabbit;
    @Autowired RabbitListenerEndpointRegistry listeners;
    @Autowired com.fiap.sast.auth.UserRepository users;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @MockitoBean GitHubClient github;
    @MockitoBean OllamaGateway ollama;
    private String token;

    @BeforeEach
    void prepare() throws Exception {
        listeners.stop();
        rabbit.execute(channel -> { channel.queuePurge(RabbitTopology.QUEUE); return null; });
        jdbc.execute("TRUNCATE analysis_outbox, analyses, app_users CASCADE");
        when(github.resolveCommitSha(any(), any())).thenReturn(SHA);
        when(github.download(any(), any())).thenAnswer(call -> snapshot(call.getArgument(0)));
        when(ollama.generate(any(), any())).thenThrow(new OllamaGateway.OllamaFailure(false));
        when(ollama.generateSuggestions(any(), any())).thenThrow(new OllamaGateway.OllamaFailure(false));
        token = register("owner@operation.test");
    }

    @Test
    @DisplayName("BDD-OP-01/10/11: concorrência, JWT, IA degradada e logs sem dados sensíveis")
    void concurrentTasksAndIsolation(CapturedOutput output) throws Exception {
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var active = new AtomicInteger();
        var maximum = new AtomicInteger();
        when(github.download(any(), any())).thenAnswer(call -> {
            maximum.accumulateAndGet(active.incrementAndGet(), Math::max);
            entered.countDown();
            try {
                if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Barreira expirou");
                return snapshot(call.getArgument(0));
            } finally { active.decrementAndGet(); }
        });
        var first = create("one");
        var second = create("two");
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM analysis_outbox", Integer.class));
        publisher.publishReady();
        assertEquals(2, queueDepth());
        listeners.start();
        try { assertTrue(entered.await(15, TimeUnit.SECONDS), "Dois consumidores devem executar em paralelo"); }
        finally { release.countDown(); }
        assertEquals(2, maximum.get());
        completed(first);
        completed(second);
        var other = register("stranger@operation.test");
        mvc.perform(get("/api/analyses/" + first).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/analyses/tasks/" + first + "/ack").header("Authorization", "Bearer " + other))
                .andExpect(status().isNoContent());
        assertEquals(false, jdbc.queryForObject("SELECT task_acknowledged FROM analyses WHERE id=?",
                Boolean.class, first));
        mvc.perform(get("/api/analyses/tasks").header("Authorization", "Bearer " + other))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/analyses/systems/acme/one/history").header("Authorization", "Bearer " + other))
                .andExpect(status().isOk()).andExpect(jsonPath("$.history.length()").value(0));
        mvc.perform(get("/api/analyses/" + first)).andExpect(status().isUnauthorized());
        assertFalse(output.getAll().contains(PASSWORD));
        assertFalse(output.getAll().contains(token));
        assertFalse(output.getAll().contains("source-secret-sentinel"));
        assertFalse(output.getAll().contains("Runtime.getRuntime().exec(cmd)"));
    }

    @Test
    @DisplayName("BDD-OP-02: entrega duplicada não inicia outra tentativa nem duplica findings")
    void duplicateDelivery() throws Exception {
        var id = create("duplicate");
        publisher.publishReady();
        listeners.start();
        completed(id);
        rabbit.convertAndSend(RabbitTopology.EXCHANGE, RabbitTopology.ROUTING_KEY, id.toString());
        await().atMost(Duration.ofSeconds(10)).until(() -> queueDepth() == 0);
        // Após a confirmação da duplicata, o claim de uma análise concluída deve continuar impossível.
        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertEquals(1, jdbc.queryForObject("SELECT attempt_count FROM analyses WHERE id=?", Integer.class, id));
            assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM findings WHERE analysis_id=?", Integer.class, id));
        });
        verify(github, times(1)).download(any(), any());
    }

    @Test
    @DisplayName("BDD-OP-03: RabbitMQ indisponível mantém outbox e recupera publicação confirmada")
    void brokerOutage() throws Exception {
        var id = create("outage");
        try {
            assertEquals(0, BROKER.execInContainer("rabbitmqctl", "stop_app").getExitCode());
            rabbit.getConnectionFactory().resetConnection();
            publisher.publishReady();
            assertEquals(1, jdbc.queryForObject("SELECT attempts FROM analysis_outbox WHERE analysis_id=?",
                    Integer.class, id));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM analysis_outbox WHERE published_at IS NULL",
                    Integer.class));
            assertEquals("QUEUED", jdbc.queryForObject("SELECT stage FROM analyses WHERE id=?", String.class, id));
        } finally {
            assertEquals(0, BROKER.execInContainer("rabbitmqctl", "start_app").getExitCode());
            rabbit.getConnectionFactory().resetConnection();
        }
        jdbc.update("UPDATE analysis_outbox SET available_at=now()-interval '1 second' WHERE analysis_id=?", id);
        publisher.publishReady();
        assertEquals(1, queueDepth());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM analysis_outbox WHERE published_at IS NULL",
                Integer.class));
        listeners.start();
        completed(id);
    }

    @Test
    @DisplayName("BDD-OP-04: lease expirado gera um evento e retoma sem duplicar dados")
    void expiredLease() throws Exception {
        var id = create("lease");
        jdbc.update("UPDATE analysis_outbox SET published_at=now() WHERE analysis_id=?", id);
        jdbc.update("UPDATE analyses SET lease_owner='worker-interrompido', lease_until=now()-interval '1 second', "
                + "attempt_count=1, commit_sha=? WHERE id=?", SHA, id);
        jdbc.update("INSERT INTO findings (id,analysis_id,rule_id,title,severity,cwe,description,file_name,"
                + "line,column_number,snippet) VALUES (?,?, 'OLD', 'Parcial', 'High', 'CWE-78', 'Parcial',"
                + " 'Old.java',1,1,'trecho parcial')", UUID.randomUUID(), id);
        reconciler.reconcile();
        reconciler.reconcile();
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM analysis_outbox "
                + "WHERE analysis_id=? AND published_at IS NULL", Integer.class, id));
        publisher.publishReady();
        listeners.start();
        completed(id);
        assertEquals(2, jdbc.queryForObject("SELECT attempt_count FROM analyses WHERE id=?", Integer.class, id));
        verify(github, never()).resolveCommitSha(any(), any());
    }

    @Test
    @DisplayName("BDD-OP-05: URL malformada não cria análise nem evento")
    void invalidOriginIsRejectedBeforePersistence() throws Exception {
        for (var url : List.of("http://github.com/acme/demo", "https://github.com.evil.test/acme/demo",
                "https://github.com/acme/../demo")) {
            mvc.perform(post("/api/analyses").header("Authorization", "Bearer " + token)
                    .contentType("application/json").content("{\"repositoryUrl\":\"" + url + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM analyses", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM analysis_outbox", Integer.class));
        verify(github, never()).download(any(), any());
    }

    @Test
    @DisplayName("BDD-OP-08: falha transitória do GitHub respeita backoff e preserva SHA na retomada")
    void retryAfterTransientSnapshotFailure() throws Exception {
        when(github.download(any(), any())).thenThrow(new GitHubClient.UnavailableException())
                .thenAnswer(call -> snapshot(call.getArgument(0)));
        var id = create("transient");
        publisher.publishReady();
        listeners.start();
        await().atMost(Duration.ofSeconds(15)).until(() -> jdbc.queryForObject(
                "SELECT next_attempt_at IS NOT NULL FROM analyses WHERE id=?", Boolean.class, id));
        listeners.stop();
        reconciler.reconcile();
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM analysis_outbox "
                + "WHERE published_at IS NULL", Integer.class));
        jdbc.update("UPDATE analyses SET lease_until=now()-interval '1 second', "
                + "next_attempt_at=now()-interval '1 second' WHERE id=?", id);
        reconciler.reconcile();
        publisher.publishReady();
        listeners.start();
        completed(id);
        assertEquals(2, jdbc.queryForObject("SELECT attempt_count FROM analyses WHERE id=?", Integer.class, id));
        verify(github, times(1)).resolveCommitSha(any(), any());
        verify(github, times(2)).download(any(), eq(SHA));
    }

    @Test
    @DisplayName("BDD-OP-09: payload inválido vai para DLQ sem iniciar download")
    void malformedMessageIsDeadLettered() {
        rabbit.convertAndSend(RabbitTopology.EXCHANGE, RabbitTopology.ROUTING_KEY, "uuid-invalido");
        listeners.start();
        await().atMost(Duration.ofSeconds(10)).until(() -> rabbit.execute(channel ->
                channel.queueDeclarePassive(RabbitTopology.DLQ).getMessageCount()) == 1);
        verify(github, never()).download(any(), any());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM analyses", Integer.class));
    }

    @Test
    @DisplayName("BDD-OP-12: API e worker inicializam banco vazio sem duplicar bootstrap")
    void concurrentBootstrapCreatesOnlyOneUser(CapturedOutput output) throws Exception {
        jdbc.execute("TRUNCATE app_users CASCADE");
        var ready = new CountDownLatch(2);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int instance = 0; instance < 2; instance++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(ready.await(5, TimeUnit.SECONDS));
                    new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                            .executeWithoutResult(status -> new com.fiap.sast.auth.BootstrapUser(
                                    users, passwords, "bootstrap-race@operation.test", PASSWORD).run(null));
                    return null;
                }));
            }
            for (var future : futures) future.get(10, TimeUnit.SECONDS);
        }
        assertEquals(1, users.count());
        assertTrue(passwords.matches(PASSWORD, users.findByEmail("bootstrap-race@operation.test")
                .orElseThrow().passwordHash));
        assertFalse(output.getAll().contains(PASSWORD));
    }

    private String register(String email) throws Exception {
        var body = "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}";
        mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isCreated());
        var response = mvc.perform(post("/api/auth/login").contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }

    private UUID create(String repository) throws Exception {
        var response = mvc.perform(post("/api/analyses").header("Authorization", "Bearer " + token)
                .contentType("application/json").content("{\"repositoryUrl\":\"https://github.com/acme/"
                        + repository + "\",\"reference\":\"main\"}"))
                .andExpect(status().isAccepted()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.stage").value("QUEUED")).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.analysisId"));
    }

    private void completed(UUID id) throws Exception {
        await().atMost(Duration.ofSeconds(20)).until(() -> !"PROCESSING".equals(jdbc.queryForObject(
                "SELECT status FROM analyses WHERE id=?", String.class, id)));
        assertEquals("COMPLETED", jdbc.queryForObject("SELECT status FROM analyses WHERE id=?", String.class, id),
                "Falha na etapa " + jdbc.queryForObject("SELECT failure_stage FROM analyses WHERE id=?", String.class, id));
        mvc.perform(get("/api/analyses/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.findings.length()").value(3))
                .andExpect(jsonPath("$.filesProcessed").value(1))
                .andExpect(jsonPath("$.commitSha").value(SHA))
                .andExpect(jsonPath("$.semanticStatus").value("DEGRADED"));
    }

    private int queueDepth() {
        return rabbit.execute(channel -> (int) channel.queueDeclarePassive(RabbitTopology.QUEUE).getMessageCount());
    }

    private static GitHubClient.Snapshot snapshot(String url) {
        return new GitHubClient.Snapshot("acme", "fixture", url, SHA,
                List.of(new GitHubClient.File("Sample.java", SOURCE)));
    }
}
