package com.fiap.sast.report;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportExportTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void sarifMapeiaSeveridadeEExcluiSugestoesConsultivas() throws Exception {
        var result = SarifReportBuilder.build(document());
        var json = mapper.writeValueAsString(result);
        var root = mapper.readValue(json, Map.class);
        var runs = (List<?>) root.get("runs");
        var run = (Map<?, ?>) runs.getFirst();
        var results = (List<?>) run.get("results");

        assertEquals(1, results.size());
        assertEquals("error", ((Map<?, ?>) results.getFirst()).get("level"));
        var tool = (Map<?, ?>) run.get("tool");
        var driver = (Map<?, ?>) tool.get("driver");
        assertEquals(1, ((List<?>) driver.get("rules")).size());
        assertEquals(1, ((Map<?, ?>) run.get("properties")).get("suggestionCount"));
    }

    @Test
    void pdfTemCapaPaginacaoEConteudoSanitizado() throws Exception {
        var pdf = PdfReportRenderer.render(document());
        var output = System.getProperty("report.visual.output");
        if (output != null && !output.isBlank()) {
            Files.write(Path.of(output), pdf);
        }
        try (var loaded = Loader.loadPDF(pdf)) {
            var text = new PDFTextStripper().getText(loaded);
            assertTrue(text.contains("FISAST - Relatório consolidado de análise"));
            assertTrue(text.contains("Risco demonstrativo"));
            assertTrue(text.contains("Sugestões consultivas"));
            assertTrue(text.contains("Página 1/"));
        }
    }

    private static Map<String, Object> document() {
        var finding = new LinkedHashMap<String, Object>();
        finding.put("findingId", "finding-1");
        finding.put("vulnerabilityCode", "VUL-000001");
        finding.put("ruleId", "SAST-JAVA-002");
        finding.put("title", "Risco demonstrativo");
        finding.put("severity", "High");
        finding.put("cwe", "CWE-78");
        finding.put("description", "Execução de comando");
        finding.put("fileName", "Example.java");
        finding.put("line", 4);
        finding.put("column", 5);
        finding.put("snippet", "Runtime.getRuntime().exec(input);");
        finding.put("expressionHash", "hash");
        finding.put("identityOrdinal", 0);

        var suggestion = Map.<String, Object>of(
                "category", "PERFORMANCE",
                "title", "Sugestão consultiva",
                "fileName", "Example.java",
                "line", 8,
                "recommendation", "Revisar");
        return Map.of(
                "analysisId", "analysis-1",
                "repositoryUrl", "https://github.com/acme/demo",
                "reference", "main",
                "status", "COMPLETED",
                "coverageStatus", "CONFIRMED",
                "filesProcessed", 1,
                "filesTotal", 1,
                "findings", List.of(finding),
                "suggestions", List.of(suggestion),
                "limitations", List.of("Limitação demonstrativa"));
    }
}
