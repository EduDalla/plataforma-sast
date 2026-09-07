package com.fiap.sast.persistence;
import org.springframework.data.jpa.repository.JpaRepository; import java.util.UUID;
public interface AnalysisRepository extends JpaRepository<Analysis,UUID> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "findings")
    java.util.Optional<Analysis> findByIdAndUserId(UUID id, UUID userId);
}
