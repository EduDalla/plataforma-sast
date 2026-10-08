package com.fiap.sast.taint;

import com.fiap.sast.parsing.JavaParserSourceParser;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Corpus rotulado do Q-01; os casos permanecem em memória e nunca são executados. */
class TaintQualityCorpusTest {
    private final JavaParserSourceParser parser = new JavaParserSourceParser();
    private final TaintAnalysisEngine engine = new TaintAnalysisEngine();

    private record Case(String id, String source, boolean vulnerable, String rationale) {}
    private record Metrics(int tp, int fp, int tn, int fn) {
        double precision() { return tp + fp == 0 ? 0 : (double) tp / (tp + fp); }
        double recall() { return tp + fn == 0 ? 0 : (double) tp / (tp + fn); }
    }

    @Test
    @org.junit.jupiter.api.DisplayName("BDD-TAINT-04: corpus rotulado calcula métricas com denominadores")
    void bddTaintCorpusCalculaMetricasComDenominadoresExplicitos() {
        // BDD-TAINT-04: Given corpus rotulado, When engine analisa, Then métricas são reproduzíveis.
        var cases = List.of(
                c("T01", "void run(@RequestParam String v){ Runtime.getRuntime().exec(v); }", true, "fonte direta"),
                c("T02", "void run(@PathVariable String v){ String x=v; Runtime.getRuntime().exec(x); }", true, "atribuição"),
                c("T03", "void run(@RequestBody String v){ Runtime.getRuntime().exec(\"ls \"+v); }", true, "concatenação"),
                c("T04", "void run(@ModelAttribute String v){ String x=v; x=x+\" -l\"; Runtime.getRuntime().exec(x); }", true, "reatribuição"),
                c("T05", "void run(HttpServletRequest r){ Runtime.getRuntime().exec(r.getParameter(\"cmd\")); }", true, "getParameter"),
                c("T06", "void run(@RequestParam String v){ Runtime.getRuntime().exec(v.isBlank()?\"ls\":v); }", true, "condicional"),
                c("T07", "void run(@RequestParam String v){ Runtime.getRuntime().exec(v); }", true, "fonte HTTP"),
                c("T08", "void run(@RequestParam String v){ new ProcessBuilder(v); }", true, "sink ProcessBuilder"),
                c("T09", "void run(@RequestParam String v){ String x=v.replaceAll(\"[^a-z]\", \"\"); Runtime.getRuntime().exec(x); }", false, "sanitizador heurístico"),
                c("T10", "void run(){ Runtime.getRuntime().exec(\"ls -la\"); }", false, "constante segura"),
                c("T11", "void run(@RequestParam String v){ execute(v); } void execute(String x){ Runtime.getRuntime().exec(x); }", true, "fora da cobertura interprocedural"),
                c("T12", "void run(@RequestParam String v){ new ProcessBuilder(\"ls\"); }", false, "ProcessBuilder constante"),
                c("T13", "void run(@RequestParam String v){ String x=\"prefix\"; Runtime.getRuntime().exec(x); }", false, "atribuição não contaminada"),
                c("T14", "void run(@RequestParam String v){ Runtime.getRuntime().exec(v.trim()); }", true, "transformação não sanitizadora"));

        var observed = cases.stream().map(item -> engine.analyze(parser.parse(wrap(item.source())), wrap(item.source()), item.id() + ".java")
                .stream().anyMatch(finding -> finding.ruleId().equals("TAINT-CMDI-001"))).toList();
        var metrics = new Metrics(0, 0, 0, 0);
        for (int i = 0; i < cases.size(); i++) {
            var expected = cases.get(i).vulnerable();
            var actual = observed.get(i);
            metrics = new Metrics(metrics.tp() + (expected && actual ? 1 : 0),
                    metrics.fp() + (!expected && actual ? 1 : 0),
                    metrics.tn() + (!expected && !actual ? 1 : 0),
                    metrics.fn() + (expected && !actual ? 1 : 0));
        }
        assertEquals(cases.size(), metrics.tp() + metrics.fp() + metrics.tn() + metrics.fn());
        assertEquals(14, cases.size());
        assertEquals(8, metrics.tp());
        assertEquals(0, metrics.fp());
        assertEquals(4, metrics.tn());
        assertEquals(2, metrics.fn());
        assertTrue(metrics.precision() >= 0 && metrics.precision() <= 1);
        assertTrue(metrics.recall() >= 0 && metrics.recall() <= 1);
    }

    private static Case c(String id, String body, boolean vulnerable, String rationale) {
        return new Case(id, body, vulnerable, rationale);
    }

    private static String wrap(String body) {
        return "import org.springframework.web.bind.annotation.*; class Corpus { " + body + " }";
    }
}
