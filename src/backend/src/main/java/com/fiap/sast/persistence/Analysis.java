package com.fiap.sast.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "analyses")
public class Analysis {
    @Id
    public UUID id = UUID.randomUUID();

    public UUID userId;
    public String repositoryUrl;
    public String repositoryOwner;
    public String repositoryName;

    @Column(name = "reference")
    public String reference;

    public String language = "java";
    public int filesAnalyzed;
    public String status = "Completed";
    public String semanticStatus = "NOT_APPLICABLE";
    public String semanticModel;
    public String promptVersion;

    public Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<Finding> findings = new ArrayList<>();
}
