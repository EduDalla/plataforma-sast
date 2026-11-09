package com.fiap.sast.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Constrói SARIF sem exportar sugestões consultivas como resultados determinísticos. */
final class SarifReportBuilder {
    private SarifReportBuilder() {
    }

    static Map<String, Object> build(Map<String, Object> document) {
        var findings = list(document.get("findings"));
        var rules = new ArrayList<Map<String, Object>>();
        var knownRules = new LinkedHashSet<String>();
        for (var finding : findings) {
            var ruleId = text(finding.get("ruleId"));
            if (knownRules.add(ruleId)) {
                var rule = new LinkedHashMap<String, Object>();
                rule.put("id", ruleId);
                rule.put("name", text(finding.get("title")));
                rule.put("shortDescription", Map.of("text", text(finding.get("description"))));
                rule.put("properties", Map.of(
                        "cwe", text(finding.get("cwe")),
                        "severity", text(finding.get("severity"))));
                rules.add(rule);
            }
        }

        var results = new ArrayList<Map<String, Object>>();
        for (var finding : findings) {
            var result = new LinkedHashMap<String, Object>();
            var ruleId = text(finding.get("ruleId"));
            var severity = text(finding.get("severity"));
            result.put("ruleId", ruleId);
            result.put("level", level(severity));
            result.put("message", Map.of("text", message(finding)));
            result.put("locations", List.of(location(finding)));
            result.put("fingerprints", Map.of("sast/v1", fingerprint(finding)));
            result.put("properties", Map.of(
                    "severity", severity,
                    "cwe", text(finding.get("cwe")),
                    "vulnerabilityCode", Objects.toString(finding.get("vulnerabilityCode"), ""),
                    "identityOrdinal", finding.getOrDefault("identityOrdinal", 0)));
            results.add(result);
        }

        var driver = new LinkedHashMap<String, Object>();
        driver.put("name", "FISAST");
        driver.put("version", "1.0");
        driver.put("informationUri", "https://github.com/fiap");
        driver.put("rules", rules);

        var run = new LinkedHashMap<String, Object>();
        run.put("tool", Map.of("driver", driver));
        run.put("automationDetails", Map.of("id", "fisast/analysis/" + text(document.get("analysisId"))));
        run.put("results", results);
        run.put("properties", Map.of(
                "coverageStatus", text(document.get("coverageStatus")),
                "suggestionCount", list(document.get("suggestions")).size(),
                "suggestionsAreConsultive", true));

        var sarif = new LinkedHashMap<String, Object>();
        sarif.put("$schema", "https://json.schemastore.org/sarif-2.1.0.json");
        sarif.put("version", "2.1.0");
        sarif.put("runs", List.of(run));
        return sarif;
    }

    private static Map<String, Object> location(Map<String, Object> finding) {
        var region = new LinkedHashMap<String, Object>();
        region.put("startLine", number(finding.get("line"), 1));
        region.put("startColumn", number(finding.get("column"), 1));
        var artifact = Map.of("uri", text(finding.get("fileName")));
        return Map.of("physicalLocation", Map.of("artifactLocation", artifact, "region", region));
    }

    private static String message(Map<String, Object> finding) {
        var title = text(finding.get("title"));
        var description = text(finding.get("description"));
        return description.isBlank() ? title : title + ": " + description;
    }

    private static String level(String severity) {
        return switch (severity) {
            case "Critical", "High" -> "error";
            case "Medium", "Low" -> "warning";
            case "Informative" -> "note";
            default -> "none";
        };
    }

    private static String fingerprint(Map<String, Object> finding) {
        var expression = text(finding.get("expressionHash"));
        var ordinal = number(finding.get("identityOrdinal"), 0);
        var findingId = text(finding.get("findingId"));
        return (expression.isBlank() ? findingId : expression) + ":" + ordinal;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private static String text(Object value) {
        return Objects.toString(value, "");
    }

    private static int number(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }
}
