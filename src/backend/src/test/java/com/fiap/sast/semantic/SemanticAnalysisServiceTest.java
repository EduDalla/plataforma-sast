package com.fiap.sast.semantic;

import com.fiap.sast.analysis.SecurityFinding;
import com.fiap.sast.parsing.JavaParserSourceParser;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class SemanticAnalysisServiceTest {
    private static final String VALID = "{\"confidence\":0.9,\"suggestedSeverity\":\"High\",\"likelyFalsePositive\":false,\"rationale\":\"Entrada alcança comando\",\"remediation\":\"Valide a entrada\"}";
    private static final String SOURCE = "class Example { void run(String input) { Runtime.getRuntime().exec(input); } }";

    /**
     * Cria um finding sintético elegível para avaliação semântica.
     *
     * @return candidato com fonte Java transitória
     */
    private static SemanticAnalysisService.Candidate candidate() {
        return new SemanticAnalysisService.Candidate(UUID.randomUUID(), new SecurityFinding(
                "SAST-JAVA-002", "Runtime.exec", "High", "CWE-78", "Comando", "Example.java", 1, 42,
                "Runtime.getRuntime().exec(input);"), SOURCE);
    }

    /**
     * Cria o serviço sob teste com limites explícitos.
     *
     * @param gateway cliente Ollama simulado
     * @param max máximo de findings avaliados
     * @param seconds orçamento total da análise em segundos
     * @return serviço configurado para o cenário de teste
     */
    private static SemanticAnalysisService service(OllamaGateway gateway, int max, int seconds) {
        return new SemanticAnalysisService(gateway, new JavaParserSourceParser(), new ObjectMapper(),
                "llama3.2:3b", max, seconds);
    }

    /**
     * Confirma que a avaliação válida não altera a severidade determinada pela regra.
     */
    @Test
    void acceptsValidAssessmentWithoutChangingFinding() {
        var finding = candidate();
        var result = service((prompt, timeout) -> {
            assertTrue(prompt.contains("<dados_nao_confiaveis>"));
            assertTrue(prompt.contains("Runtime.getRuntime().exec(input)"));
            return VALID;
        }, 10, 45).enrich(List.of(finding));
        assertEquals("COMPLETED", result.status());
        assertEquals("High", result.assessments().get(finding.findingId()).suggestedSeverity());
        assertEquals("High", finding.finding().severity());
    }

    /**
     * Confirma que somente falhas transitórias recebem uma segunda tentativa.
     */
    @Test
    void retriesOnlyTransientFailures() {
        var attempts = new AtomicInteger();
        var result = service((prompt, timeout) -> {
            if (attempts.incrementAndGet() == 1) throw new OllamaGateway.OllamaFailure(true);
            return VALID;
        }, 10, 45).enrich(List.of(candidate()));
        assertEquals(2, attempts.get());
        assertEquals("COMPLETED", result.status());
        attempts.set(0);
        var timedOut = service((prompt, timeout) -> {
            attempts.incrementAndGet();
            throw new OllamaGateway.OllamaFailure(true);
        }, 10, 45).enrich(List.of(candidate()));
        assertEquals("DEGRADED", timedOut.status());
        assertEquals(2, attempts.get());
    }

    /**
     * Confirma degradação segura para JSON inválido e modelo indisponível.
     */
    @Test
    void degradesForInvalidJsonExtraFieldAndUnavailableModel() {
        for (String response : List.of("not json", VALID.replace("}", ",\"extra\":1}"),
                VALID.replace("\"High\"", "\"Extreme\""))) {
            var result = service((prompt, timeout) -> response, 10, 45).enrich(List.of(candidate()));
            assertEquals("DEGRADED", result.status());
            assertTrue(result.assessments().isEmpty());
        }
        var attempts = new AtomicInteger();
        var unavailable = service((prompt, timeout) -> {
            attempts.incrementAndGet();
            throw new OllamaGateway.OllamaFailure(false);
        }, 10, 45).enrich(List.of(candidate()));
        assertEquals("DEGRADED", unavailable.status());
        assertEquals(1, attempts.get());
    }

    /**
     * Confirma que a quantidade e o orçamento de tempo limitam a avaliação.
     */
    @Test
    void respectsCandidateAndTimeBudgets() {
        var calls = new AtomicInteger();
        OllamaGateway gateway = (prompt, timeout) -> {
            calls.incrementAndGet();
            return VALID;
        };
        var result = service(gateway, 1, 45).enrich(List.of(candidate(), candidate()));
        assertEquals("DEGRADED", result.status());
        assertEquals(1, calls.get());
        assertEquals(1, result.assessments().size());
        assertEquals("DEGRADED", service(gateway, 10, 0).enrich(List.of(candidate())).status());
        assertEquals(1, calls.get());
        assertEquals("NOT_APPLICABLE", service(gateway, 10, 45).enrich(List.of()).status());
    }
}
