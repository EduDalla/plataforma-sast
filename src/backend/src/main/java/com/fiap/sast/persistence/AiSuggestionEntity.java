package com.fiap.sast.persistence;

import com.fiap.sast.semantic.AiSuggestion;
import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "ai_suggestions")
public class AiSuggestionEntity {
    @Id public UUID id = UUID.randomUUID();
    @ManyToOne @JoinColumn(name = "analysis_id", nullable = false)
    public Analysis analysis;
    public String category;
    @Column(length = 16)
    public String severity;
    @Column(length = 160)
    public String title;
    @Column(length = 1000)
    public String fileName;
    public int lineNumber;
    @Column(length = 300)
    public String evidence;
    @Column(length = 600)
    public String rationale;
    public double confidence;
    @Column(length = 800)
    public String recommendation;
    @Column(length = 500)
    public String limitations;
    @Column(length = 100)
    public String model;
    @Column(length = 32)
    public String promptVersion;

    public static AiSuggestionEntity from(Analysis analysis, AiSuggestion value) {
        var entity = new AiSuggestionEntity();
        entity.analysis = analysis;
        entity.category = value.category();
        entity.severity = value.severity();
        entity.title = value.title();
        entity.fileName = value.fileName();
        entity.lineNumber = value.line();
        entity.evidence = value.evidence();
        entity.rationale = value.rationale();
        entity.confidence = value.confidence();
        entity.recommendation = value.recommendation();
        entity.limitations = value.limitations();
        entity.model = value.model();
        entity.promptVersion = value.promptVersion();
        return entity;
    }

    public AiSuggestion toValue() {
        return new AiSuggestion(category, severity, title, fileName, lineNumber, evidence, rationale,
                confidence, recommendation, limitations, model, promptVersion);
    }
}
