package com.fiap.sast.quality;

import com.fiap.sast.analysis.SastEngine;
import com.fiap.sast.parsing.JavaParserSourceParser;
import com.fiap.sast.rules.DeserializationRule;
import com.fiap.sast.rules.HardcodedCredentialRule;
import com.fiap.sast.rules.RuntimeExecRule;
import com.fiap.sast.rules.SecurityRule;
import com.fiap.sast.taint.TaintAnalysisEngine;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Corpus sintético e autorizado para medir as regras sem compilar ou executar o código analisado.
 *
 * <p>Os rótulos representam a revisão do caso, não uma cópia do resultado do engine. Um caso é
 * avaliado somente contra a regra indicada para que os denominadores permaneçam explícitos.</p>
 */
class SastQualityCorpusTest {
    private final JavaParserSourceParser parser = new JavaParserSourceParser();

    private record CorpusCase(String id, String ruleId, String source, boolean vulnerable, String rationale) {
    }

    private record Metrics(int tp, int fp, int tn, int fn) {
        double precision() {
            return tp + fp == 0 ? 0 : (double) tp / (tp + fp);
        }

        double recall() {
            return tp + fn == 0 ? 0 : (double) tp / (tp + fn);
        }
    }

    @Test
    @DisplayName("TASK-06: corpus SAST calcula métricas por regra com FP e FN rastreáveis")
    void corpusCalculaMetricasPorRegra() {
        var rules = Map.of(
                "SAST-JAVA-001", new HardcodedCredentialRule(),
                "SAST-JAVA-002", new RuntimeExecRule(),
                "SAST-JAVA-003", new DeserializationRule(),
                "TAINT-CMDI-001", new TaintAnalysisEngine());

        var cases = List.of(
                c("HC-01", "SAST-JAVA-001", "class C { String password = \"secret\"; }", true,
                        "literal em variável sensível"),
                c("HC-02", "SAST-JAVA-001", "class C { void set(){ config.apiKey = \"abc\"; } Object config; }", true,
                        "atribuição em propriedade sensível"),
                c("HC-03", "SAST-JAVA-001", "class C { String token = System.getenv(\"TOKEN\"); }", false,
                        "origem externa"),
                c("HC-04", "SAST-JAVA-001", "class C { String token = \"placeholder\"; }", false,
                        "placeholder rotulado como não secreto; falso positivo conhecido da heurística nominal"),
                c("HC-05", "SAST-JAVA-001", "class C { String credential = \"secret\"; }", true,
                        "nome de credencial fora do catálogo; falso negativo conhecido"),

                c("RE-01", "SAST-JAVA-002", "class C { void run(String x) { Runtime.getRuntime().exec(x); } }", true,
                        "cadeia estrutural suportada"),
                c("RE-02", "SAST-JAVA-002", "class C { void run(String x) { process.execute(x); } Object process; }", false,
                        "método diferente de Runtime.exec"),
                c("RE-03", "SAST-JAVA-002", "class C { void run(String x) { Runtime r = Runtime.getRuntime(); r.exec(x); } }", true,
                        "alias de Runtime não acompanhado; falso negativo conhecido"),
                c("RE-04", "SAST-JAVA-002", "class C { void exec(String x) {} void run(String x) { exec(x); } }", false,
                        "método local sem chamada de Runtime"),

                c("DS-01", "SAST-JAVA-003", "import java.io.*; class C { Object run(InputStream s) throws Exception { ObjectInputStream in = new ObjectInputStream(s); return in.readObject(); } }", true,
                        "variável local ObjectInputStream"),
                c("DS-02", "SAST-JAVA-003", "class C { Object run(Other in) { return in.readObject(); } } class Other { Object readObject(){ return null; } }", false,
                        "tipo não pertencente ao sink"),
                c("DS-03", "SAST-JAVA-003", "import java.io.*; class C { Object run(InputStream s) throws Exception { return new ObjectInputStream(s).readObject(); } }", true,
                        "expressão direta não indexada; falso negativo conhecido"),
                c("DS-04", "SAST-JAVA-003", "import java.io.*; class C { void run(InputStream s) throws Exception { ObjectInputStream in = new ObjectInputStream(s); in.readUTF(); } }", false,
                        "operação segura diferente de readObject"),

                c("TA-01", "TAINT-CMDI-001", "class C { void run(@org.springframework.web.bind.annotation.RequestParam String x) { Runtime.getRuntime().exec(x); } }", true,
                        "fonte HTTP direta em Runtime.exec"),
                c("TA-02", "TAINT-CMDI-001", "class C { void run(@org.springframework.web.bind.annotation.RequestParam String x) { new ProcessBuilder(x); } }", true,
                        "fonte HTTP em ProcessBuilder"),
                c("TA-03", "TAINT-CMDI-001", "class C { void run(@org.springframework.web.bind.annotation.RequestParam String x) { String safe = x.replaceAll(\"[^a-z]\", \"\"); Runtime.getRuntime().exec(safe); } }", false,
                        "replaceAll de dois argumentos quebra o rastro pela convenção heurística"),
                c("TA-04", "TAINT-CMDI-001", "class C { void run(@org.springframework.web.bind.annotation.RequestParam String x) { execute(x); } void execute(String y) { Runtime.getRuntime().exec(y); } }", true,
                        "fluxo entre métodos fora do escopo intraprocedural; falso negativo conhecido"),
                c("TA-05", "TAINT-CMDI-001", "class C { void run() { new ProcessBuilder(\"ls\"); } }", false,
                        "ProcessBuilder com argumento constante"));

        var observed = cases.stream().map(item -> {
            var rule = rules.get(item.ruleId());
            var findings = new SastEngine(parser, List.<SecurityRule>of(rule)).analyze(item.source(), item.id() + ".java");
            return Map.entry(item, findings.stream().anyMatch(finding -> item.ruleId().equals(finding.ruleId())));
        }).toList();

        var metricsByRule = observed.stream().collect(Collectors.groupingBy(
                entry -> entry.getKey().ruleId(), Collectors.collectingAndThen(Collectors.toList(), entries -> {
                    var metrics = new Metrics(0, 0, 0, 0);
                    for (var entry : entries) {
                        var expected = entry.getKey().vulnerable();
                        var actual = entry.getValue();
                        metrics = new Metrics(metrics.tp() + (expected && actual ? 1 : 0),
                                metrics.fp() + (!expected && actual ? 1 : 0),
                                metrics.tn() + (!expected && !actual ? 1 : 0),
                                metrics.fn() + (expected && !actual ? 1 : 0));
                    }
                    return metrics;
                })));

        assertEquals(new Metrics(2, 1, 1, 1), metricsByRule.get("SAST-JAVA-001"));
        assertEquals(new Metrics(1, 0, 2, 1), metricsByRule.get("SAST-JAVA-002"));
        assertEquals(new Metrics(1, 0, 2, 1), metricsByRule.get("SAST-JAVA-003"));
        assertEquals(new Metrics(2, 0, 2, 1), metricsByRule.get("TAINT-CMDI-001"));
        assertEquals(cases.size(), observed.size());
        metricsByRule.values().forEach(metrics -> {
            assertTrue(metrics.precision() >= 0 && metrics.precision() <= 1);
            assertTrue(metrics.recall() >= 0 && metrics.recall() <= 1);
        });
    }

    private static CorpusCase c(String id, String ruleId, String source, boolean vulnerable, String rationale) {
        return new CorpusCase(id, ruleId, source, vulnerable, rationale);
    }
}
