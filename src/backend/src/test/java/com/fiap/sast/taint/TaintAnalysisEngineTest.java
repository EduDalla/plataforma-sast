package com.fiap.sast.taint;

import com.fiap.sast.parsing.JavaParserSourceParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaintAnalysisEngineTest {
    private final JavaParserSourceParser parser = new JavaParserSourceParser();
    private final TaintAnalysisEngine engine = new TaintAnalysisEngine();

    @Test
    void parametroHttpConcatenadoAtingindoRuntimeExecGeraFindingComTraceCompleto() {
        var source = "class Controller {\n"
                + "  void run(@org.springframework.web.bind.annotation.RequestParam String cmd) throws Exception {\n"
                + "    String full = \"ls \" + cmd;\n"
                + "    Runtime.getRuntime().exec(full);\n"
                + "  }\n"
                + "}";

        var findings = engine.analyze(parser.parse(source), source, "Controller.java");

        assertEquals(1, findings.size());
        var finding = findings.getFirst();
        assertEquals("TAINT-CMDI-001", finding.ruleId());
        assertEquals("CWE-78", finding.cwe());
        assertNotNull(finding.taintTrace());
        assertEquals("http_param", finding.taintTrace().source().kind());
        assertEquals("sink", finding.taintTrace().sink().kind());
        assertTrue(finding.taintTrace().steps().stream().anyMatch(step -> step.kind().equals("concatenation")));
        assertTrue(finding.taintTrace().steps().stream().anyMatch(step -> step.kind().equals("assignment")));
    }

    @Test
    void valorConstanteNaoContaminadoNaoGeraFinding() {
        var source = "class Controller {\n"
                + "  void run() throws Exception {\n"
                + "    Runtime.getRuntime().exec(\"ls -la\");\n"
                + "  }\n"
                + "}";

        var findings = engine.analyze(parser.parse(source), source, "Controller.java");

        assertTrue(findings.isEmpty());
    }

    @Test
    void sanitizadorReplaceAllQuebraORastroDeTaint() {
        var source = "class Controller {\n"
                + "  void run(@org.springframework.web.bind.annotation.RequestParam String cmd) throws Exception {\n"
                + "    String safe = cmd.replaceAll(\"[^a-zA-Z]\", \"\");\n"
                + "    Runtime.getRuntime().exec(safe);\n"
                + "  }\n"
                + "}";

        var findings = engine.analyze(parser.parse(source), source, "Controller.java");

        assertTrue(findings.isEmpty());
    }

    @Test
    void fluxoInterproceduralNaoEhRastreado() {
        var source = "class Controller {\n"
                + "  void run(@org.springframework.web.bind.annotation.RequestParam String cmd) throws Exception {\n"
                + "    execute(cmd);\n"
                + "  }\n"
                + "  void execute(String value) throws Exception {\n"
                + "    Runtime.getRuntime().exec(value);\n"
                + "  }\n"
                + "}";

        var findings = engine.analyze(parser.parse(source), source, "Controller.java");

        assertTrue(findings.isEmpty());
    }

    @Test
    void propagacaoAtravesDeMultiplasReatribuicoesAindaEhDetectada() {
        var source = "class Controller {\n"
                + "  void run(@org.springframework.web.bind.annotation.RequestParam String cmd) throws Exception {\n"
                + "    String a = cmd;\n"
                + "    String b = a;\n"
                + "    b = b + \" --force\";\n"
                + "    Runtime.getRuntime().exec(b);\n"
                + "  }\n"
                + "}";

        var findings = engine.analyze(parser.parse(source), source, "Controller.java");

        assertEquals(1, findings.size());
        assertEquals("http_param", findings.getFirst().taintTrace().source().kind());
    }
}
