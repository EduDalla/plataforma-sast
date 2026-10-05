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
        AnalysisJobService.replaceSuggestion(analysis, suggestion("Security", "outro arquivo", "Other.java", 7));

        assertEquals(3, analysis.suggestions.size());
        assertEquals("mais recente", analysis.suggestions.stream()
                .filter(item -> item.lineNumber == 7 && item.fileName.equals("Example.java"))
                .findFirst().orElseThrow().title);
        assertEquals("outra linha", analysis.suggestions.stream()
                .filter(item -> item.lineNumber == 8).findFirst().orElseThrow().title);
        assertEquals("outro arquivo", analysis.suggestions.stream()
                .filter(item -> item.lineNumber == 7 && item.fileName.equals("Other.java"))
                .findFirst().orElseThrow().title);
    }

    private static AiSuggestion suggestion(String category, String title, int line) {
        return suggestion(category, title, "Example.java", line);
    }

    private static AiSuggestion suggestion(String category, String title, String fileName, int line) {
        return new AiSuggestion(category, "Medium", title, fileName, line,
                "int value = 1;", "rationale", 0.8, "recommendation", "limitations",
                "llama3.2:3b", "prompt");
    }
}
