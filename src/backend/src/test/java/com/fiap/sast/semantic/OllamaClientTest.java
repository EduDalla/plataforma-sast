package com.fiap.sast.semantic;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class OllamaClientTest {
    /**
     * Confirma o contrato HTTP estruturado e sem streaming enviado ao Ollama.
     */
    @Test
    void sendsStructuredNonStreamingRequestAndReadsEnvelope() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var requestBody = new AtomicReference<String>();
        server.createContext("/api/generate", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var response = "{\"response\":\"{\\\"confidence\\\":0.8}\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();
        try {
            var client = new OllamaClient(new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort(), "llama3.2:3b");
            assertEquals("{\"confidence\":0.8}", client.generate("finding", Duration.ofSeconds(2)));
            var body = new ObjectMapper().readTree(requestBody.get());
            assertEquals("llama3.2:3b", body.get("model").asText());
            assertFalse(body.get("stream").booleanValue());
            assertEquals("object", body.get("format").get("type").asText());
        } finally {
            server.stop(0);
        }
    }

    /**
     * Confirma a classificação de indisponibilidade e de erro transitório do serviço.
     */
    @Test
    void classifiesUnavailableModelAndTransientServerError() throws Exception {
        for (int code : new int[]{404, 503}) {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/generate", exchange -> {
                exchange.sendResponseHeaders(code, -1);
                exchange.close();
            });
            server.start();
            try {
                var client = new OllamaClient(new ObjectMapper(),
                        "http://127.0.0.1:" + server.getAddress().getPort(), "llama3.2:3b");
                var failure = assertThrows(OllamaGateway.OllamaFailure.class,
                        () -> client.generate("finding", Duration.ofSeconds(2)));
                assertEquals(code == 503, failure.retryable());
            } finally {
                server.stop(0);
            }
        }
    }

    /**
     * Confirma que o timeout de rede pode ser repetido dentro do orçamento.
     */
    @Test
    void timeoutIsRetryable() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/generate", exchange -> {
            try {
                Thread.sleep(250);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        server.start();
        try {
            var client = new OllamaClient(new ObjectMapper(),
                    "http://127.0.0.1:" + server.getAddress().getPort(), "llama3.2:3b");
            assertTrue(assertThrows(OllamaGateway.OllamaFailure.class,
                    () -> client.generate("finding", Duration.ofMillis(30))).retryable());
        } finally {
            server.stop(0);
        }
    }

    /**
     * Impede que uma configuração envie contexto de código a um endpoint externo.
     */
    @Test
    void rejectsExternalOllamaEndpoint() {
        assertThrows(IllegalArgumentException.class,
                () -> new OllamaClient(new ObjectMapper(), "https://external.example", "llama3.2:3b"));
    }
}
