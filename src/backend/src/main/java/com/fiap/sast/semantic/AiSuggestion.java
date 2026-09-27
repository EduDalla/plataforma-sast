package com.fiap.sast.semantic;

/** Hipótese consultiva produzida pelo modelo, independente de um finding SAST. */
public record AiSuggestion(String category, String severity, String title, String fileName, int line,
        String evidence, String rationale, double confidence, String recommendation,
        String limitations, String model, String promptVersion) {}
