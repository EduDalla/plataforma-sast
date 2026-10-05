package com.fiap.sast.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class CompletedResultCacheTest {
    @Test
    void armazenaApenasResultadosConcluidosPorUsuarioDuranteQuinzeMinutos() {
        var time = new AtomicLong();
        var cache = new CompletedResultCache(time::get);
        var owner = UUID.randomUUID();
        var completed = result(UUID.randomUUID(), "COMPLETED", "safe");
        var legacyCompleted = result(UUID.randomUUID(), "Completed", "legacy");
        var processing = result(UUID.randomUUID(), "PROCESSING", "partial");

        cache.put(owner, completed);
        cache.put(owner, legacyCompleted);
        cache.put(owner, processing);

        assertSame(completed, cache.get(owner, completed.analysisId()));
        assertSame(legacyCompleted, cache.get(owner, legacyCompleted.analysisId()));
        assertNull(cache.get(UUID.randomUUID(), completed.analysisId()));
        assertNull(cache.get(owner, processing.analysisId()));
        time.set(Duration.ofMinutes(15).toNanos());
        assertNull(cache.get(owner, completed.analysisId()));
    }

    @Test
    void rejeitaRespostaGrandeEEvitaCrescimentoIlimitado() {
        var cache = new CompletedResultCache(System::nanoTime);
        var owner = UUID.randomUUID();
        var oversized = result(UUID.randomUUID(), "COMPLETED", "x".repeat(1_100_000));
        cache.put(owner, oversized);
        assertNull(cache.get(owner, oversized.analysisId()));

        AnalysisController.Result first = null;
        AnalysisController.Result last = null;
        for (int i = 0; i < 25; i++) {
            last = result(UUID.randomUUID(), "COMPLETED", "x".repeat(800_000));
            if (first == null) first = last;
            cache.put(owner, last);
        }
        assertNull(cache.get(owner, first.analysisId()));
        assertSame(last, cache.get(owner, last.analysisId()));
    }

    private static AnalysisController.Result result(UUID id, String status, String snippet) {
        var finding = new AnalysisController.FindingResult("RULE", "Title", "High", "CWE-1",
                "Description", "Example.java", 1, 1, snippet, null, null);
        return new AnalysisController.Result(id, status, "https://github.com/acme/demo", "main",
                "java", 1, null, "NOT_APPLICABLE", "NOT_APPLICABLE", List.of(), List.of(finding),
                new AnalysisController.ResultSummary(1, 0, 1, 0, 0, 0, "High"),
                status, 1, 1, null, null);
    }
}
