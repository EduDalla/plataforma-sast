package com.fiap.sast.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisFileCoverageRepository extends JpaRepository<AnalysisFileCoverage, UUID> {
    /** Remove a cobertura parcial da tentativa antes de uma nova execução.
     * @param analysisId identificador da análise
     */
    void deleteByAnalysisId(UUID analysisId);

    /** Confirma que o arquivo e a regra foram processados pela análise.
     * @param analysisId identificador da análise
     * @param fileName arquivo processado
     * @param ruleId regra avaliada
     * @return {@code true} quando a cobertura foi registrada como processada
     */
    boolean existsByAnalysisIdAndFileNameAndRuleIdAndProcessedTrue(
            UUID analysisId, String fileName, String ruleId);
}
