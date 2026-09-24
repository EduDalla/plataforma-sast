package com.fiap.sast.persistence;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "findings")
public class Finding {
    @Id
    public UUID id = UUID.randomUUID();

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "analysis_id")
    public Analysis analysis;

    public String ruleId;
    public String title;
    public String severity;
    public String cwe;
    public String description;
    public String fileName;
    public int line;

    @Column(name = "column_number")
    public int column;

    public String snippet;

    @Column(name = "taint_trace")
    public String taintTrace;

    @OneToOne(mappedBy = "finding", cascade = CascadeType.ALL, orphanRemoval = true)
    public AiAssessmentEntity aiAssessment;
}
