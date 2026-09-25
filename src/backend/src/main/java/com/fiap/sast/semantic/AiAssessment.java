package com.fiap.sast.semantic;

import java.util.List;

public record AiAssessment(String model, String promptVersion, double confidence,
        String suggestedSeverity, boolean likelyFalsePositive, String rationale, String remediation,
        String risk, List<String> evidence, String falsePositiveReason, String limitations,
        List<String> recommendations) {
    public AiAssessment(String model, String promptVersion, double confidence,
            String suggestedSeverity, boolean likelyFalsePositive, String rationale, String remediation) {
        this(model, promptVersion, confidence, suggestedSeverity, likelyFalsePositive, rationale, remediation,
                null, List.of(), null, null, List.of());
    }
}
