package com.fiap.sast.semantic;

import com.fiap.sast.analysis.SastEngine;
import com.fiap.sast.parsing.JavaParserSourceParser;
import com.fiap.sast.rules.DeserializationRule;
import com.fiap.sast.rules.HardcodedCredentialRule;
import com.fiap.sast.rules.RuntimeExecRule;
import com.fiap.sast.taint.TaintAnalysisEngine;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "SAST_RUN_LIVE_OLLAMA", matches = "true")
class LiveOllamaDemoTest {
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

    private final JavaParserSourceParser parser = new JavaParserSourceParser();
    private final SastEngine engine = new SastEngine(parser, List.of(new HardcodedCredentialRule(),
            new RuntimeExecRule(), new DeserializationRule(), new TaintAnalysisEngine()));
    private final String model = System.getenv().getOrDefault("SAST_OLLAMA_MODEL", "llama3.2:3b");
    private final ObjectMapper mapper = new ObjectMapper();
    private final SemanticAnalysisService semantic = new SemanticAnalysisService(
            new OllamaClient(mapper, System.getenv().getOrDefault("SAST_OLLAMA_BASE_URL", "http://ollama:11434"), model),
            parser, mapper, model, 10, 240);

    /**
     * Demonstra que um fixture vulnerável recebe três avaliações sem executar o fonte.
     */
    @Test
    void officialSampleHasThreeAssessedFindingsWithoutExecutingSource() throws Exception {
        var findings = engine.analyze(VULNERABLE_SOURCE, "InMemoryVulnerable.java");
        assertEquals(3, findings.size());
        var candidates = findings.stream()
                .map(finding -> new SemanticAnalysisService.Candidate(UUID.randomUUID(), finding, VULNERABLE_SOURCE))
                .toList();
        var result = semantic.enrich(candidates);
        assertEquals("COMPLETED", result.status());
        assertEquals(3, result.assessments().size());
    }

    /**
     * Demonstra uma avaliação consultiva para um finding de taint com trace completo.
     */
    @Test
    void httpInputReachingRuntimeExecHasTraceAndAssessment() {
        var source = "class Controller {\n"
                + "  void run(@org.springframework.web.bind.annotation.RequestParam String cmd) throws Exception {\n"
                + "    Runtime.getRuntime().exec(cmd);\n"
                + "  }\n"
                + "}";
        var finding = engine.analyze(source, "Controller.java").stream()
                .filter(item -> "TAINT-CMDI-001".equals(item.ruleId()))
                .findFirst()
                .orElseThrow();
        assertNotNull(finding.taintTrace());
        var candidate = new SemanticAnalysisService.Candidate(UUID.randomUUID(), finding, source);
        var result = semantic.enrich(List.of(candidate));
        assertEquals("COMPLETED", result.status());
        assertEquals(1, result.assessments().size());
    }

    /** BDD-IA-04: seis findings rotulados recebem avaliação real do modelo local. */
    @Test
    @org.junit.jupiter.api.DisplayName("BDD-IA-04: seis findings recebem avaliações reais do modelo local")
    void sixLabeledFindingsReceiveRealAssessments() {
        var firstSourceFindings = engine.analyze(VULNERABLE_SOURCE, "InMemoryVulnerable.java");
        var secondSource = "class Commands {\n"
                + "  void a() throws Exception { Runtime.getRuntime().exec(\"a\"); }\n"
                + "  void b() throws Exception { Runtime.getRuntime().exec(\"b\"); }\n"
                + "  void c() throws Exception { Runtime.getRuntime().exec(\"c\"); }\n"
                + "}";
        var secondSourceFindings = engine.analyze(secondSource, "Commands.java");
        var candidates = java.util.stream.Stream.concat(
                        firstSourceFindings.stream().map(f -> new SemanticAnalysisService.Candidate(UUID.randomUUID(), f, VULNERABLE_SOURCE)),
                        secondSourceFindings.stream().map(f -> new SemanticAnalysisService.Candidate(UUID.randomUUID(), f, secondSource)))
                .limit(6)
                .toList();
        assertEquals(6, candidates.size());
        var result = semantic.enrich(candidates);
        assertEquals("COMPLETED", result.status());
        assertEquals(6, result.assessments().size());
    }

    /** Demonstra uma hipótese de N+1 sem criar finding determinístico. */
    @Test
    void loopedRepositoryLookupProducesConsultiveSuggestion() {
        var source = "class Orders {\n"
                + "  void load(java.util.List<Long> ids) {\n"
                + "    for (Long id : ids) { repository.findById(id); }\n"
                + "  }\n"
                + "}";
        assertTrue(engine.analyze(source, "Orders.java").isEmpty());
        var scan = new SemanticSuggestionService(
                new OllamaClient(mapper, System.getenv().getOrDefault("SAST_OLLAMA_BASE_URL", "http://ollama:11434"), model),
                parser, mapper, model, 4, 60);
        var result = scan.scan(List.of(new SemanticSuggestionService.SourceFile("Orders.java", source)));
        assertEquals("COMPLETED", result.status());
        assertTrue(result.suggestions().stream().anyMatch(item -> "PERFORMANCE".equals(item.category())
                && item.line() == 3));
    }
}
