package com.fiap.sast.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByEmail(String email);

    /**
     * Serializa o bootstrap entre instâncias da API e do worker no mesmo banco PostgreSQL.
     *
     * @return marcador após adquirir o lock, liberado automaticamente no fim da transação
     */
    @org.springframework.data.jpa.repository.Query(
            value = "select 1 from pg_advisory_xact_lock(638019001)", nativeQuery = true)
    Integer acquireBootstrapLock();
}
