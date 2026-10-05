package com.fiap.sast.analysis;

import com.fiap.sast.parsing.JavaParserSourceParser;
import com.fiap.sast.parsing.InvalidJavaSourceException;
import com.fiap.sast.rules.DeserializationRule;
import com.fiap.sast.rules.HardcodedCredentialRule;
import com.fiap.sast.rules.RuntimeExecRule;
import com.fiap.sast.rules.SecurityRule;
import com.github.javaparser.ast.CompilationUnit;
import com.fiap.sast.taint.TaintAnalysisEngine;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SastEngineTest {
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

    @Test
    void fixtureVulneravelProduzTresFindingsComTodosOsCampos() {
        var engine = new SastEngine(new JavaParserSourceParser(), List.of(
                new HardcodedCredentialRule(), new RuntimeExecRule(), new DeserializationRule(),
                new TaintAnalysisEngine()));

        var findings = engine.analyze(VULNERABLE_SOURCE, "InMemoryVulnerable.java");

        assertEquals(3, findings.size());
        assertEquals(List.of("SAST-JAVA-001", "SAST-JAVA-002", "SAST-JAVA-003"),
                findings.stream().map(SecurityFinding::ruleId).toList());
        findings.forEach(finding -> {
            assertFalse(finding.ruleId().isBlank());
            assertFalse(finding.severity().isBlank());
            assertFalse(finding.cwe().isBlank());
            assertEquals("InMemoryVulnerable.java", finding.fileName());
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
    void preservaAchadosDeRegrasDiferentesNaMesmaLinha() {
        var source = "class Example { void run() throws Exception { String password = \"x\"; Runtime.getRuntime().exec(\"x\"); } }";
        var findings = new SastEngine(new JavaParserSourceParser(), List.of(
                new HardcodedCredentialRule(), new RuntimeExecRule()))
                .analyze(source, "One.java");

        assertEquals(List.of("SAST-JAVA-001", "SAST-JAVA-002"), findings.stream()
                .map(SecurityFinding::ruleId).toList());
    }

    @Test
    void preservaDoisAchadosDaMesmaRegraNaMesmaLinha() {
        var source = "class Example { void run() { String password = \"x\", token = \"y\"; } }";
        var findings = new SastEngine(new JavaParserSourceParser(), List.of(new HardcodedCredentialRule()))
                .analyze(source, "One.java");

        assertEquals(2, findings.size());
        assertEquals(List.of("SAST-JAVA-001", "SAST-JAVA-001"), findings.stream()
                .map(SecurityFinding::ruleId).toList());
        assertNotEquals(findings.get(0).column(), findings.get(1).column());
    }

    @Test
    void deduplicaAmesmaOcorrenciaEmitidaMaisDeUmaVez() {
        var duplicate = new SecurityRule() {
            @Override
            public String ruleId() {
                return "TEST-DUPLICATE";
            }

            @Override
            public List<SecurityFinding> analyze(CompilationUnit ast, String source, String file) {
                var finding = new SecurityFinding(ruleId(), "Teste", "Low", "CWE-TEST",
                        "Ocorrência repetida", file, 1, 1, "class Example {}");
                return List.of(finding, finding);
            }
        };

        var findings = new SastEngine(new JavaParserSourceParser(), List.of(duplicate))
                .analyze("class Example {}", "Example.java");

        assertEquals(1, findings.size());
        assertEquals("TEST-DUPLICATE", findings.getFirst().ruleId());
    }

    @Test
    void rejeitaFonteJavaInvalidaSemProduzirFinding() {
        var engine = new SastEngine(new JavaParserSourceParser(), List.of(new RuntimeExecRule()));

        var exception = assertThrows(InvalidJavaSourceException.class,
                () -> engine.analyze("class Broken {", "Broken.java"));

        assertEquals("Broken.java", exception.fileName);
    }

    @Test
    void naoAlertaPadraoSeguroSemCredencialNemExecucao() {
        var source = "class SafeExample { void run(String value) {"
                + " String password = readFromVault();"
                + " logger.info(value); }"
                + " String readFromVault() { return \"configured\"; } }";
        var engine = new SastEngine(new JavaParserSourceParser(), List.of(
                new HardcodedCredentialRule(), new RuntimeExecRule(), new DeserializationRule()));

        assertTrue(engine.analyze(source, "SafeExample.java").isEmpty());
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
}
