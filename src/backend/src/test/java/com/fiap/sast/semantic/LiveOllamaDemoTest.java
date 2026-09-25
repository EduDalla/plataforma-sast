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
            parser, mapper, model, 10, 45);

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
}
