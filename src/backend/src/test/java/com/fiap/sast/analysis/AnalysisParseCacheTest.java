package com.fiap.sast.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import com.fiap.sast.parsing.JavaParserSourceParser;
import com.fiap.sast.parsing.JavaSourceParser;
import com.fiap.sast.rules.HardcodedCredentialRule;
import com.fiap.sast.rules.RuntimeExecRule;
import com.fiap.sast.semantic.OllamaGateway;
import com.fiap.sast.semantic.SemanticAnalysisService;
import com.fiap.sast.semantic.SemanticSuggestionService;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AnalysisParseCacheTest {
    @Test
    void reutilizaUmParseNasTresEtapasDoWorker() {
        var count = new AtomicInteger();
        JavaSourceParser parser = source -> {
            count.incrementAndGet();
            return new JavaParserSourceParser().parse(source);
        };
        OllamaGateway gateway = new OllamaGateway() {
            @Override
            public String generate(String prompt, Duration timeout) {
                return "{\"confidence\":0.9,\"suggestedSeverity\":\"High\","
                        + "\"likelyFalsePositive\":false,\"rationale\":\"Risco\","
                        + "\"remediation\":\"Corrigir\",\"risk\":\"Comando\","
                        + "\"evidence\":[\"linha 3\"],\"falsePositiveReason\":\"Nenhum\","
                        + "\"limitations\":\"Local\",\"recommendations\":[\"Validar\"]}";
            }

            @Override
            public String generateSuggestions(String prompt, Duration timeout) {
                return "{\"suggestions\":[]}";
            }
        };
        var source = "class Example {\n"
                + "  String password = \"secret\";\n"
                + "  void run() throws Exception { Runtime.getRuntime().exec(\"x\"); }\n}";
        var engine = new SastEngine(parser, List.of(new HardcodedCredentialRule(), new RuntimeExecRule()));
        var semantic = new SemanticAnalysisService(gateway, parser, new ObjectMapper(), "model", 1, 10);
        var suggestions = new SemanticSuggestionService(gateway, parser, new ObjectMapper(), "model", 1, 10);

        try (var cache = engine.newParseCache()) {
            var findings = engine.analyze(source, "Example.java", cache);
            var candidate = new SemanticAnalysisService.Candidate(UUID.randomUUID(), findings.getFirst(), source);
            semantic.enrich(List.of(candidate), cache);
            suggestions.scan(List.of(new SemanticSuggestionService.SourceFile("Example.java", source)),
                    ignored -> {}, cache);
            assertEquals(1, count.get());
        }
    }

    @Test
    void compartilhaAstNaTentativaSemAlterarFindingsEDescartaAoFechar() {
        var count = new AtomicInteger();
        JavaSourceParser parser = source -> {
            count.incrementAndGet();
            return new JavaParserSourceParser().parse(source);
        };
        var engine = new SastEngine(parser, List.of(new HardcodedCredentialRule()));
        var source = "class Example { String password = \"secret\"; void run() {} }";

        try (var cache = engine.newParseCache()) {
            assertEquals(engine.analyze(source, "Example.java"),
                    engine.analyze(source, "Example.java", cache));
            var ast = cache.ast("Example.java", source);
            assertEquals(1, cache.methods("Example.java", source).size());
            assertEquals(2, count.get());
            assertEquals(ast, cache.ast("Example.java", source));
            assertNotSame(ast, cache.ast("Example.java", new String(source)));
            assertEquals(3, count.get());
        }
    }
}
