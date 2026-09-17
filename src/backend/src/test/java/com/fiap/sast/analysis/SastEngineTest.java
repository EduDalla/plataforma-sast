package com.fiap.sast.analysis;

import com.fiap.sast.parsing.JavaParserSourceParser;
import com.fiap.sast.rules.DeserializationRule;
import com.fiap.sast.rules.HardcodedCredentialRule;
import com.fiap.sast.rules.RuntimeExecRule;
import com.fiap.sast.taint.TaintAnalysisEngine;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SastEngineTest {
    @Test
    void exemploOficialProduzTresFindingsComTodosOsCampos() throws Exception {
        var sample = findSample();
        var source = Files.readString(sample);
        var engine = new SastEngine(new JavaParserSourceParser(), List.of(
                new HardcodedCredentialRule(), new RuntimeExecRule(), new DeserializationRule(),
                new TaintAnalysisEngine()));

        var findings = engine.analyze(source, "samples/VulnerableExample.java");

        assertEquals(3, findings.size());
        assertEquals(List.of("SAST-JAVA-001", "SAST-JAVA-002", "SAST-JAVA-003"),
                findings.stream().map(SecurityFinding::ruleId).toList());
        findings.forEach(finding -> {
            assertFalse(finding.ruleId().isBlank());
            assertFalse(finding.severity().isBlank());
            assertFalse(finding.cwe().isBlank());
            assertEquals("samples/VulnerableExample.java", finding.fileName());
            assertTrue(finding.line() > 0);
            assertTrue(finding.column() > 0);
            assertFalse(finding.snippet().isBlank());
        });
    }

    @Test
    void taintAnalysisEngineParticipaDoPipelineJuntoComAsRegrasDeterministicas() {
        var source = "class Controller {\n"
                + "  void run(@org.springframework.web.bind.annotation.RequestParam String cmd) throws Exception {\n"
                + "    String password = \"123456\";\n"
                + "    Runtime.getRuntime().exec(cmd);\n"
                + "  }\n"
                + "}";
        var engine = new SastEngine(new JavaParserSourceParser(), List.of(
                new HardcodedCredentialRule(), new RuntimeExecRule(), new DeserializationRule(),
                new TaintAnalysisEngine()));

        var findings = engine.analyze(source, "Controller.java");

        assertEquals(List.of("SAST-JAVA-001", "SAST-JAVA-002", "TAINT-CMDI-001"),
                findings.stream().map(SecurityFinding::ruleId).toList());
        var taintFinding = findings.stream().filter(f -> f.ruleId().equals("TAINT-CMDI-001")).findFirst().orElseThrow();
        assertNotNull(taintFinding.taintTrace());
        assertEquals("http_param", taintFinding.taintTrace().source().kind());
    }

    @Test
    void analiseNaoExecutaInicializadorDoCodigoFonte() throws Exception {
        var marker = Path.of(System.getProperty("java.io.tmpdir"),
                "sast-source-must-not-run-" + System.nanoTime());
        var source = "class Untrusted { static { try { java.nio.file.Files.writeString(java.nio.file.Path.of(\""
                + marker + "\"), \"executed\"); } catch (Exception ignored) {} } }";
        var engine = new SastEngine(new JavaParserSourceParser(), List.of());

        engine.analyze(source, "Untrusted.java");

        assertFalse(Files.exists(marker));
    }

    private static Path findSample() {
        var candidates = List.of(
                Path.of("samples/VulnerableExample.java"),
                Path.of("../../samples/VulnerableExample.java"));
        return candidates.stream().map(Path::toAbsolutePath).filter(Files::exists).findFirst()
                .orElseThrow(() -> new AssertionError("A amostra oficial não foi encontrada"));
    }
}
