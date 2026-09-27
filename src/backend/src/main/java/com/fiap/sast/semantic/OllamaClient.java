package com.fiap.sast.semantic;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class OllamaClient implements OllamaGateway {
    private static final int MAX_RESPONSE_BYTES = 16384;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
    private final ObjectMapper mapper;
    private final URI endpoint;
    private final String model;

    /**
     * Configura o cliente HTTP do Ollama com endpoint e modelo definidos pelo ambiente.
     *
     * @param mapper serializador JSON da aplicação
     * @param baseUrl endereço interno permitido para o serviço Ollama
     * @param model identificador do modelo local a ser consultado
     */
    public OllamaClient(ObjectMapper mapper, @Value("${sast.ollama.base-url}") String baseUrl,
            @Value("${sast.ollama.model}") String model) {
        this.mapper = mapper;
        this.endpoint = endpoint(baseUrl);
        this.model = model;
    }

    /**
     * Envia um prompt ao Ollama e devolve somente o campo de resposta estruturada.
     *
     * @param prompt conteúdo da avaliação semântica
     * @param timeout tempo máximo permitido para a requisição
     * @return JSON textual produzido pelo modelo
     * @throws OllamaFailure quando o serviço falha ou viola os limites de resposta
     */
    @Override
    public String generate(String prompt, Duration timeout) throws OllamaFailure {
        return generateWithSchema(prompt, timeout, assessmentSchema());
    }

    @Override
    public String generateSuggestions(String prompt, Duration timeout) throws OllamaFailure {
        return generateWithSchema(prompt, timeout, suggestionSchema(), true);
    }

    private static Map<String, Object> assessmentSchema() {
        return Map.of(
                "type", "object", "additionalProperties", false,
                "required", new String[]{"confidence", "suggestedSeverity", "likelyFalsePositive",
                        "rationale", "remediation", "risk", "evidence", "falsePositiveReason",
                        "limitations", "recommendations"},
                "properties", Map.of(
                        "confidence", Map.of("type", "number", "minimum", 0, "maximum", 1),
                        "suggestedSeverity", Map.of("type", "string", "enum", new String[]{"Low", "Medium", "High", "Critical"}),
                        "likelyFalsePositive", Map.of("type", "boolean"),
                        "rationale", Map.of("type", "string"), "remediation", Map.of("type", "string"),
                        "risk", Map.of("type", "string"),
                        "evidence", Map.of("type", "array", "items", Map.of("type", "string")),
                        "falsePositiveReason", Map.of("type", "string"),
                        "limitations", Map.of("type", "string"),
                        "recommendations", Map.of("type", "array", "items", Map.of("type", "string"))));
    }

    private static Map<String, Object> suggestionSchema() {
        var item = Map.of("type", "object", "additionalProperties", false,
                "required", new String[]{"category", "severity", "title", "rationale", "confidence", "recommendation", "limitations"},
                "properties", Map.of(
                        "category", Map.of("type", "string", "enum", new String[]{"SECURITY", "PERFORMANCE"}),
                        "severity", Map.of("type", "string", "enum", new String[]{"Low", "Medium", "High", "Critical"}),
                        "title", Map.of("type", "string", "maxLength", 100),
                        "rationale", Map.of("type", "string", "maxLength", 280),
                        "confidence", Map.of("type", "number", "minimum", 0, "maximum", 1),
                        "recommendation", Map.of("type", "string", "maxLength", 280),
                        "limitations", Map.of("type", "string", "maxLength", 180)));
        return Map.of("type", "object", "additionalProperties", false,
                "required", new String[]{"suggestions"},
                "properties", Map.of("suggestions", Map.of("type", "array", "maxItems", 1, "items", item)));
    }

    private String generateWithSchema(String prompt, Duration timeout, Map<String, Object> schema) throws OllamaFailure {
        return generateWithSchema(prompt, timeout, schema, false);
    }

    private String generateWithSchema(String prompt, Duration timeout, Map<String, Object> schema,
            boolean compact) throws OllamaFailure {
        try {
            var body = mapper.writeValueAsString(Map.of(
                    "model", model,
                    "prompt", prompt,
                    "stream", false,
                    "format", schema,
                    "options", compact ? Map.of("temperature", 0, "num_predict", 192)
                            : Map.of("temperature", 0)));
            var request = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (var input = response.body()) {
                if (response.statusCode() != 200) {
                    throw new OllamaFailure(response.statusCode() == 429 || response.statusCode() >= 500);
                }
                var bytes = input.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (bytes.length > MAX_RESPONSE_BYTES) {
                    throw new OllamaFailure(false);
                }
                var content = mapper.readTree(new String(bytes, StandardCharsets.UTF_8));
                if (content == null || content.get("response") == null || !content.get("response").isTextual()) {
                    throw new OllamaFailure(false);
                }
                return content.get("response").asText();
            }
        } catch (OllamaFailure failure) {
            throw failure;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new OllamaFailure(false);
        } catch (IOException failure) {
            throw new OllamaFailure(true);
        } catch (RuntimeException failure) {
            throw new OllamaFailure(false);
        }
    }

    /**
     * Valida o endereço administrativo do Ollama antes de montar o endpoint da API.
     *
     * @param baseUrl endereço configurado pelo ambiente
     * @return endpoint de geração do Ollama
     * @throws IllegalArgumentException quando a URL não representa uma origem HTTP válida
     */
    private static URI endpoint(String baseUrl) {
        var normalized = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        var uri = URI.create(normalized);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getHost() == null
                || !isLocalOllamaHost(uri.getHost())
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException(
                    "SAST_OLLAMA_BASE_URL deve apontar para o Ollama local ou da rede interna");
        }
        return URI.create(normalized + "/api/generate");
    }

    /**
     * Restringe o destino do contexto de código ao serviço local ou à rede interna do Compose.
     *
     * @param host host extraído da URL configurada
     * @return {@code true} quando o host não representa um destino externo
     */
    private static boolean isLocalOllamaHost(String host) {
        return "ollama".equals(host)
                || "localhost".equals(host)
                || "127.0.0.1".equals(host)
                || "[::1]".equals(host)
                || "::1".equals(host);
    }
}
