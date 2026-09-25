package com.fiap.sast.persistence;

import com.fiap.sast.semantic.AiAssessment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "ai_assessments")
public class AiAssessmentEntity {
    @Id
    public UUID findingId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "finding_id")
    public Finding finding;

    public String model;
    public String promptVersion;
    public double confidence;
    public String suggestedSeverity;
    public boolean likelyFalsePositive;

    @Column(length = 500)
    public String rationale;

    @Column(length = 1000)
    public String remediation;

    @Column(length = 1200)
    public String risk;

    @Column(length = 1600)
    public String evidence;

    @Column(length = 800)
    public String falsePositiveReason;

    @Column(length = 800)
    public String limitations;

    @Column(length = 1600)
    public String recommendations;

    /**
     * Converte a avaliação de domínio em uma entidade associada ao finding.
     *
     * @param finding finding persistido que receberá a avaliação
     * @param value avaliação validada retornada pelo serviço semântico
     * @return entidade pronta para persistência
     */
    public static AiAssessmentEntity from(Finding finding, AiAssessment value) {
        var entity = new AiAssessmentEntity();
        entity.finding = finding;
        entity.model = value.model();
        entity.promptVersion = value.promptVersion();
        entity.confidence = value.confidence();
        entity.suggestedSeverity = value.suggestedSeverity();
        entity.likelyFalsePositive = value.likelyFalsePositive();
        entity.rationale = value.rationale();
        entity.remediation = value.remediation();
        entity.risk = value.risk();
        entity.evidence = String.join("\u001f", value.evidence());
        entity.falsePositiveReason = value.falsePositiveReason();
        entity.limitations = value.limitations();
        entity.recommendations = String.join("\u001f", value.recommendations());
        return entity;
    }

    /**
     * Reconstrói a avaliação de domínio a partir dos campos persistidos.
     *
     * @return avaliação consultiva associada ao finding
     */
    public AiAssessment toValue() {
        return new AiAssessment(model, promptVersion, confidence, suggestedSeverity,
                likelyFalsePositive, rationale, remediation, risk,
                split(evidence), falsePositiveReason, limitations, split(recommendations));
    }

    private static List<String> split(String value) {
        return value == null || value.isBlank() ? List.of() : Arrays.asList(value.split("\\u001f", -1));
    }
}
