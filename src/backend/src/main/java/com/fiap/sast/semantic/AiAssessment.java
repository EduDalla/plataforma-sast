package com.fiap.sast.semantic;

public record AiAssessment(String model, String promptVersion, double confidence,
        String suggestedSeverity, boolean likelyFalsePositive, String rationale, String remediation) {}
