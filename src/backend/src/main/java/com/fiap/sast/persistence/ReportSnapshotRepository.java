package com.fiap.sast.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Acesso sempre delimitado à conta proprietária dos snapshots de relatório. */
public interface ReportSnapshotRepository extends JpaRepository<ReportSnapshot, UUID> {
    /**
     * Lista versões pertencentes à análise e à conta autenticada.
     *
     * @param analysisId análise de origem
     * @param userId proprietário autenticado
     * @return versões mais recentes primeiro
     */
    List<ReportSnapshot> findByAnalysisIdAndUserIdOrderByVersionDesc(UUID analysisId, UUID userId);

    /**
     * Obtém uma versão sem permitir acesso cruzado entre contas.
     *
     * @param analysisId análise de origem
     * @param userId proprietário autenticado
     * @param version versão solicitada
     * @return snapshot encontrado, se autorizado
     */
    Optional<ReportSnapshot> findByAnalysisIdAndUserIdAndVersion(UUID analysisId, UUID userId, int version);

    /**
     * Recupera a última versão automática ou manual.
     *
     * @param analysisId análise de origem
     * @param userId proprietário autenticado
     * @return última versão, se existir
     */
    Optional<ReportSnapshot> findTopByAnalysisIdAndUserIdOrderByVersionDesc(UUID analysisId, UUID userId);
}
