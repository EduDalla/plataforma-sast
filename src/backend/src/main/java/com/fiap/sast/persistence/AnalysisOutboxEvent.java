package com.fiap.sast.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Evento mínimo de recuperação: o payload publicado é somente o ID da análise. */
@Entity
@Table(name = "analysis_outbox")
public class AnalysisOutboxEvent {
    @Id public UUID id = UUID.randomUUID();
    @Column(name = "analysis_id", nullable = false) public UUID analysisId;
    @Column(name = "event_type", nullable = false, length = 64) public String eventType = "ANALYSIS_REQUESTED";
    @Column(name = "created_at", nullable = false) public Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    @Column(name = "available_at", nullable = false) public Instant availableAt = createdAt;
    @Column(name = "published_at") public Instant publishedAt;
    @Column(name = "attempts", nullable = false) public int attempts;
    @Column(name = "last_error", length = 500) public String lastError;
}
