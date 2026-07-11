package io.taskflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per issued refresh token. We store a SHA-256 hash of the token, not the
 * token itself — a DB compromise must not yield usable refresh tokens.
 *
 * <p><b>Refresh-token rotation</b>: every {@code /auth/refresh} call issues a new
 * refresh token and marks the previous row's {@code replaced_by_id} so we can detect
 * token reuse. If a revoked or replaced token is ever presented again, that's evidence
 * of theft → we revoke the entire family (every descendant) and force re-login.</p>
 *
 * <p>{@code session_id} groups all rotations of a single login session — useful for
 * "log out of this device" UX without revoking every other session.</p>
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 128, updatable = false)
    private String tokenHash;

    /** Groups all rotations of a single logical login session. */
    @Column(name = "session_id", nullable = false, columnDefinition = "uuid", updatable = false)
    private UUID sessionId;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** Set when this token has been rotated → points at the new token's id. */
    @Column(name = "replaced_by_id", columnDefinition = "uuid")
    private UUID replacedById;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    public boolean isActive(Instant now) {
        return revokedAt == null && replacedById == null && expiresAt.isAfter(now);
    }
}
