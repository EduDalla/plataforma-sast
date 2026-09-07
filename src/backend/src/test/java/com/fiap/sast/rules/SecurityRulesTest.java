package com.fiap.sast.rules;

import com.fiap.sast.parsing.JavaParserSourceParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityRulesTest {
    private final JavaParserSourceParser parser = new JavaParserSourceParser();

    @Test
    void trechoNaoArmazenaArquivoInteiroEmUmaLinha() {
        var source = "class Example { String password = \"" + "x".repeat(1000) + "\"; void unrelated() {} }";
        var finding = new HardcodedCredentialRule().analyze(parser.parse(source), source, "Example.java").getFirst();
        assertTrue(finding.snippet().length() <= 500);
        assertTrue(!finding.snippet().contains("class Example") && !finding.snippet().contains("unrelated"));
    }

    @Test
    void detectaCredencialHardcodedEIgnoraOrigemSegura() {
        var source = "class Example {\n"
                + "  String PASSWORD = \"123\";\n"
                + "  String secret = provider.getSecret();\n"
                + "  String token = System.getenv(\"TOKEN\");\n"
                + "  String pwd = \"\";\n"
                + "  void update() { config.apiKey = \"abc\"; }\n"
                + "}";

        var findings = new HardcodedCredentialRule().analyze(parser.parse(source), source, "Example.java");

        assertEquals(2, findings.size());
        assertEquals("SAST-JAVA-001", findings.get(0).ruleId());
        assertTrue(findings.stream().anyMatch(f -> f.snippet().contains("PASSWORD")));
        assertTrue(findings.stream().anyMatch(f -> f.snippet().contains("apiKey")));
    }

    @Test
    void detectaRuntimeExecSomenteNaCadeiaEsperada() {
        var source = "class Example {\n"
                + "  void run(String input) throws Exception {\n"
                + "    Runtime.getRuntime().exec(input);\n"
                + "    process.execute(input);\n"
                + "  }\n"
                + "}";

        var findings = new RuntimeExecRule().analyze(parser.parse(source), source, "Example.java");

        assertEquals(1, findings.size());
        assertEquals("SAST-JAVA-002", findings.get(0).ruleId());
        assertEquals("CWE-78", findings.get(0).cwe());
    }

    @Test
    void detectaReadObjectDoObjectInputStreamEIgnoraOutrosMetodos() {
        var source = "import java.io.ObjectInputStream;\n"
                + "class Example {\n"
                + "  Object read(java.io.InputStream stream) throws Exception {\n"
                + "    ObjectInputStream input = new ObjectInputStream(stream);\n"
                + "    input.readObject();\n"
                + "    input.readUTF();\n"
                + "    other.readObject();\n"
                + "    return null;\n"
                + "  }\n"
                + "  Other other;\n"
                + "}\n"
                + "class Other { Object readObject() { return null; } }";

        var findings = new DeserializationRule().analyze(parser.parse(source), source, "Example.java");

        assertEquals(1, findings.size());
        assertEquals("SAST-JAVA-003", findings.get(0).ruleId());
        assertEquals("CWE-502", findings.get(0).cwe());
    }
}
