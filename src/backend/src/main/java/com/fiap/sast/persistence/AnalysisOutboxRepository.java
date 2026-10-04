package com.fiap.sast.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalysisOutboxRepository extends JpaRepository<AnalysisOutboxEvent, UUID> {
    @Query("select e from AnalysisOutboxEvent e where e.publishedAt is null and e.availableAt <= :now order by e.createdAt")
    List<AnalysisOutboxEvent> findReady(@Param("now") Instant now);

    boolean existsByAnalysisIdAndPublishedAtIsNull(UUID analysisId);

    @Modifying
    @Query("update AnalysisOutboxEvent e set e.publishedAt = :publishedAt where e.id = :id and e.publishedAt is null")
    int markPublished(@Param("id") UUID id, @Param("publishedAt") Instant publishedAt);
}
