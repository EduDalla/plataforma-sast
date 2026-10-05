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
import org.hibernate.annotations.BatchSize;

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
    public String stage = "QUEUED";
    public int filesProcessed;
    public int filesTotal;
    public String failureStage;
    @Column(length = 500)
    public String failureMessage;
    public String semanticStatus = "NOT_APPLICABLE";
    public String semanticModel;
    public String promptVersion;
    public String suggestionStatus = "NOT_APPLICABLE";
    public String snapshotHash;
    @Column(name = "commit_sha", length = 40)
    public String commitSha;
    @Column(name = "attempt_count", nullable = false)
    public int attemptCount;
    @Column(name = "lease_owner", length = 100)
    public String leaseOwner;
    @Column(name = "lease_until")
    public Instant leaseUntil;
    @Column(name = "next_attempt_at")
    public Instant nextAttemptAt;
    public boolean taskAcknowledged = true;

    public Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    public List<Finding> findings = new ArrayList<>();

    @OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    public List<AiSuggestionEntity> suggestions = new ArrayList<>();
}
