package io.taskflow.repository;

import io.taskflow.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Mass-revoke every still-active token sharing a session id. Called on token-
     * reuse detection (theft signal) and on explicit "log out of this device".
     */
    @Modifying
    @Query("""
            update RefreshToken r
               set r.revokedAt = :now
             where r.sessionId = :sessionId
               and r.revokedAt is null
            """)
    int revokeSession(UUID sessionId, Instant now);

    @Modifying
    @Query("""
            update RefreshToken r
               set r.revokedAt = :now
             where r.user.id = :userId
               and r.revokedAt is null
            """)
    int revokeAllForUser(UUID userId, Instant now);
}
