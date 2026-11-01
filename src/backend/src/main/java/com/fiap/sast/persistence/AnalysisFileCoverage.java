package com.fiap.sast.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Metadado mínimo usado para não validar uma ausência com cobertura desconhecida. */
@Entity
@Table(name = "analysis_file_coverage")
public class AnalysisFileCoverage {
    @Id
    public UUID id = UUID.randomUUID();
    public UUID analysisId;
    @Column(length = 1000)
    public String fileName;
    public String ruleId;
    public boolean processed;
}
