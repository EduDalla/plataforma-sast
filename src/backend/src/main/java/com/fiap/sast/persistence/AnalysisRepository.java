package com.fiap.sast.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.UUID;
public interface AnalysisRepository extends JpaRepository<Analysis,UUID> {
    java.util.List<Analysis> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId);

    /**
     * Carrega apenas execuções concluídas para compor a central do usuário.
     *
     * @param userId identificador do usuário proprietário
     * @return execuções concluídas em ordem decrescente de criação
     */
    @Query("select a from Analysis a where a.userId = :userId and a.status in ('COMPLETED', 'Completed') "
            + "order by a.createdAt desc, a.id desc")
    java.util.List<Analysis> findCompletedByUser(@Param("userId") UUID userId);

    /**
     * Carrega execuções concluídas de um repositório pertencente ao usuário.
     *
     * @param userId identificador do usuário proprietário
     * @param owner proprietário do repositório no GitHub
     * @param repository nome do repositório no GitHub
     * @return execuções concluídas em ordem decrescente de criação
     */
    @Query("select a from Analysis a where a.userId = :userId and a.repositoryOwner = :owner "
            + "and a.repositoryName = :repository and a.status in ('COMPLETED', 'Completed') "
            + "order by a.createdAt desc, a.id desc")
    java.util.List<Analysis> findCompletedHistory(@Param("userId") UUID userId,
            @Param("owner") String owner, @Param("repository") String repository);

    /**
     * Carrega tarefas em curso ou encerradas que ainda aguardam confirmação.
     *
     * @param userId identificador do usuário proprietário
     * @return tarefas visíveis em ordem decrescente de criação
     */
    @Query("select a from Analysis a where a.userId = :userId and (a.status = 'PROCESSING' "
            + "or (a.status in ('COMPLETED', 'Completed', 'FAILED', 'Failed') and a.taskAcknowledged = false)) "
            + "order by a.createdAt desc, a.id desc")
    java.util.List<Analysis> findVisibleTasks(@Param("userId") UUID userId);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "findings")
    java.util.Optional<Analysis> findByIdAndUserId(UUID id, UUID userId);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "findings")
    java.util.Optional<Analysis> findFirstByUserIdAndRepositoryOwnerAndRepositoryNameAndReferenceOrderByCreatedAtDescIdDesc(
            UUID userId, String repositoryOwner, String repositoryName, String reference);

    @Modifying
    @Query("update Analysis a set a.leaseOwner = :owner, a.leaseUntil = :until, a.attemptCount = a.attemptCount + 1 "
            + "where a.id = :id and a.status = 'PROCESSING' and (a.leaseUntil is null or a.leaseUntil < :now)")
    int claim(@Param("id") UUID id, @Param("owner") String owner, @Param("until") Instant until,
            @Param("now") Instant now);

    @Query("select a from Analysis a where a.status = 'PROCESSING' and a.leaseUntil is not null and a.leaseUntil < :now")
    java.util.List<Analysis> findExpiredLeases(@Param("now") Instant now);
}
