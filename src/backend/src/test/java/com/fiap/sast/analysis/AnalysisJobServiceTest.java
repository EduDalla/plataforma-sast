package com.fiap.sast.analysis;

import com.fiap.sast.persistence.Analysis;
import com.fiap.sast.semantic.AiSuggestion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnalysisJobServiceTest {
    @Test
    void substituiSugestaoIndependenteNaMesmaLinhaEPreservaOutras() {
        var analysis = new Analysis();
        AnalysisJobService.replaceSuggestion(analysis, suggestion("Performance", "antiga", 7));
        AnalysisJobService.replaceSuggestion(analysis, suggestion("Security", "mais recente", 7));
        AnalysisJobService.replaceSuggestion(analysis, suggestion("Performance", "outra linha", 8));

        assertEquals(2, analysis.suggestions.size());
        assertEquals("mais recente", analysis.suggestions.stream()
                .filter(item -> item.lineNumber == 7).findFirst().orElseThrow().title);
        assertEquals("outra linha", analysis.suggestions.stream()
                .filter(item -> item.lineNumber == 8).findFirst().orElseThrow().title);
    }

    private static AiSuggestion suggestion(String category, String title, int line) {
        return new AiSuggestion(category, "Medium", title, "Example.java", line,
                "int value = 1;", "rationale", 0.8, "recommendation", "limitations",
                "llama3.2:3b", "prompt");
    }
}
