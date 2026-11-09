package com.fiap.sast.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Snapshot imutável e sanitizado usado para reexportar uma análise. */
@Entity
@Table(name = "analysis_report_snapshots")
public class ReportSnapshot {
    @Id
    public UUID id = UUID.randomUUID();
    public UUID analysisId;
    public UUID userId;
    public int version;
    public Instant generatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    public String analysisStatus;
    public String coverageStatus;
    public String schemaVersion;
    public String locale;
    @Column(name = "snapshot_hash", length = 64, nullable = false)
    public String snapshotHash;
    @Column(name = "report_payload", columnDefinition = "TEXT", nullable = false)
    public String payload;
}
