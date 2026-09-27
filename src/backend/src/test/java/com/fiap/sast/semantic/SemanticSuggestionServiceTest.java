package com.fiap.sast.semantic;

import com.fiap.sast.parsing.JavaParserSourceParser;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class SemanticSuggestionServiceTest {
    private static final String SOURCE = "class Orders {\n"
            + "  void load(java.util.List<Long> ids) {\n"
            + "    for (Long id : ids) { repository.findById(id); }\n"
            + "  }\n"
            + "  void loadOther(java.util.List<Long> ids) {\n"
            + "    for (Long id : ids) { repository.findById(id); }\n"
            + "  }\n"
            + "}";
    private static final String VALID = "{\"suggestions\":[{\"category\":\"PERFORMANCE\",\"severity\":\"Medium\"," 
            + "\"title\":\"Possível N+1\","
            + "\"rationale\":\"Consulta dentro do loop\",\"confidence\":0.8,"
            + "\"recommendation\":\"Busque em lote\",\"limitations\":\"Depende das consultas em execução\"}]}";

    private static SemanticSuggestionService service(OllamaGateway gateway, int max, int budget) {
        return new SemanticSuggestionService(gateway, new JavaParserSourceParser(), new ObjectMapper(),
                "llama3.2:3b", max, budget);
    }

    private static List<SemanticSuggestionService.SourceFile> files() {
        return List.of(new SemanticSuggestionService.SourceFile("Orders.java", SOURCE));
    }

    @Test void suggestsNPlusOneWithoutDeterministicFinding() {
        var result = service((prompt, timeout) -> {
            assertTrue(prompt.contains("repository.findById(id)"));
            assertTrue(prompt.contains("<dados_nao_confiaveis>"));
            return VALID;
        }, 1, 60).scan(files());
        assertEquals("DEGRADED", result.status());
        assertEquals(1, result.suggestions().size());
        assertEquals(3, result.suggestions().get(0).line());
    }

    @Test void acceptsEmptyAnswerAndRejectsUnexpectedFields() {
        var empty = service((prompt, timeout) -> "{\"suggestions\":[]}", 2, 60).scan(files());
        assertEquals("COMPLETED", empty.status());
        assertTrue(empty.suggestions().isEmpty());
        var invalid = service((prompt, timeout) -> VALID.replace("\"title\":", "\"evidence\":\"inventedCall()\",\"title\":"),
                2, 60).scan(files());
        assertEquals("DEGRADED", invalid.status());
        assertTrue(invalid.suggestions().isEmpty());
        var invalidSeverity = service((prompt, timeout) -> VALID.replace("Medium", "Urgent"), 1, 60).scan(files());
        assertEquals("DEGRADED", invalidSeverity.status());
        assertTrue(invalidSeverity.suggestions().isEmpty());
    }

    @Test void acceptsTheSameFourSeverityLevelsUsedByFindings() {
        for (var severity : List.of("Critical", "High", "Medium", "Low")) {
            var result = service((prompt, timeout) -> VALID.replace("Medium", severity), 1, 60).scan(files());
            assertEquals(severity, result.suggestions().get(0).severity());
        }
    }

    @Test void extractsEvidenceFromSourceEvenWhenModelDoesNotRepeatIt() {
        var result = service((prompt, timeout) -> VALID, 1, 60).scan(files());
        assertEquals("for (Long id : ids) { repository.findById(id); }", result.suggestions().get(0).evidence());
        assertEquals(3, result.suggestions().get(0).line());
    }

    @Test void ignoresGenericLoopsAndKeepsRelevantOperationInShortContext() {
        var filler = new StringBuilder("class Example {\n  void generic() { for (int i=0; i<10; i++) { log(i); } }\n")
                .append("  void load(java.util.List<Long> ids) {\n");
        for (int index = 0; index < 80; index++) filler.append("    int unused").append(index).append(" = ").append(index).append(";\n");
        filler.append("    for (Long id : ids) { repository.findById(id); }\n  }\n}");
        var result = service((prompt, timeout) -> {
            assertTrue(prompt.contains("repository.findById(id)"));
            assertFalse(prompt.contains("unused0"));
            assertTrue(prompt.length() < 2600);
            return "{\"suggestions\":[]}";
        }, 1, 60).scan(List.of(new SemanticSuggestionService.SourceFile("Example.java", filler.toString())));
        assertEquals("COMPLETED", result.status());
    }

    @Test void limitsCallsAndDegradesWhenOllamaUnavailable() {
        var calls = new AtomicInteger();
        var limited = service((prompt, timeout) -> {
            calls.incrementAndGet();
            return "{\"suggestions\":[]}";
        }, 1, 60).scan(files());
        assertEquals(1, calls.get());
        assertEquals("DEGRADED", limited.status());
        var unavailable = service((prompt, timeout) -> {
            throw new OllamaGateway.OllamaFailure(false);
        }, 2, 60).scan(files());
        assertEquals("DEGRADED", unavailable.status());
        assertEquals("DEGRADED", service((prompt, timeout) -> VALID, 2, 0).scan(files()).status());
    }
}
