package io.taskflow.security;

import io.taskflow.config.SecurityProperties;
import io.taskflow.domain.RefreshToken;
import io.taskflow.domain.User;
import io.taskflow.exception.UnauthorizedException;
import io.taskflow.repository.RefreshTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Manages opaque refresh tokens.
 *
 * <p><b>Why opaque, not JWT?</b> A JWT refresh token is unrevocable until expiry —
 * fine for short-lived access tokens, fatal for long-lived refresh tokens. By storing
 * a SHA-256 <em>hash</em> of a random 256-bit value, we get both server-side
 * revocability and DB-leak resistance.</p>
 *
 * <p><b>Rotation flow</b> (RFC 6749 §10.4 + IETF OAuth security BCP):</p>
 * <pre>
 *   /auth/refresh:
 *     1. Look up presented token by SHA-256 hash.
 *     2. If row is missing  → 401 (forged/expired).
 *     3. If row is revoked OR already replaced
 *           → THEFT: revoke the entire session (every token with same session_id) → 401.
 *     4. If row is expired  → 401 (no implicit reuse-detection penalty).
 *     5. Otherwise: mark current as replaced_by_id pointing at the new row,
 *        persist new token, return new (token, expiry) pair.
 * </pre>
 *
 * <p>This service intentionally hands raw tokens to the controller as a small DTO,
 * which then sets the HttpOnly cookie. We never log or echo the raw token.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32; // 256 bits

    private final RefreshTokenRepository repository;
    private final SecurityProperties properties;

    /**
     * Issue a brand-new refresh token (new session). Use this on login.
     */
    @Transactional
    public IssuedRefreshToken issueNewSession(User user, HttpServletRequest request) {
        UUID sessionId = UUID.randomUUID();
        return issue(user, sessionId, request);
    }

    /**
     * Validate the presented refresh token and rotate it. Returns the user the
     * token belongs to alongside the new token. Throws {@link UnauthorizedException}
     * for any failure.
     */
    @Transactional
    public RotationResult rotate(String presentedToken, HttpServletRequest request) {
        String hash = sha256Hex(presentedToken);
        RefreshToken stored = repository.findByTokenHash(hash)
                .orElseThrow(() -> {
                    log.info("Refresh attempted with unknown token (possible forgery)");
                    return new UnauthorizedException("INVALID_REFRESH", "Invalid refresh token");
                });

        Instant now = Instant.now();

        // Token reuse → strong signal of theft. Revoke the entire session family.
        if (stored.getRevokedAt() != null || stored.getReplacedById() != null) {
            log.warn("Refresh-token reuse detected for session {} — revoking session", stored.getSessionId());
            repository.revokeSession(stored.getSessionId(), now);
            throw new UnauthorizedException("REFRESH_REUSED",
                    "Refresh token reuse detected — session terminated");
        }

        if (!stored.getExpiresAt().isAfter(now)) {
            throw new UnauthorizedException("REFRESH_EXPIRED", "Refresh token expired");
        }

        // Mint the replacement first so we can record its id on the old row.
        User user = stored.getUser();
        IssuedRefreshToken replacement = issue(user, stored.getSessionId(), request);
        stored.setReplacedById(replacement.entityId());
        repository.save(stored);
        return new RotationResult(user, replacement);
    }

    @Transactional
    public void revokeSession(UUID sessionId) {
        repository.revokeSession(sessionId, Instant.now());
    }

    @Transactional
    public void revokeAllForUser(UUID userId) {
        repository.revokeAllForUser(userId, Instant.now());
    }

    /**
     * Best-effort logout: revoke the session associated with the presented refresh
     * token. We don't throw on an unknown/expired token — logout should be idempotent
     * (a user clicking "log out" twice is a UX nothing, not a 401).
     */
    @Transactional
    public void revokeByPresentedToken(String presentedToken) {
        if (presentedToken == null || presentedToken.isBlank()) return;
        repository.findByTokenHash(sha256Hex(presentedToken))
                .ifPresent(rt -> repository.revokeSession(rt.getSessionId(), Instant.now()));
    }

    // ----------------------------------------------------------- internals

    private IssuedRefreshToken issue(User user, UUID sessionId, HttpServletRequest request) {
        String raw = generateOpaqueToken();
        String hash = sha256Hex(raw);
        Instant now = Instant.now();
        Instant exp = now.plus(properties.jwt().refreshTokenTtl());

        RefreshToken entity = RefreshToken.builder()
                .user(user)
                .tokenHash(hash)
                .sessionId(sessionId)
                .expiresAt(exp)
                .userAgent(truncate(request.getHeader("User-Agent"), 255))
                .ipAddress(clientIp(request))
                .build();

        repository.save(entity);
        return new IssuedRefreshToken(entity.getId(), sessionId, raw, exp);
    }

    private static String generateOpaqueToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String clientIp(HttpServletRequest req) {
        // Honor X-Forwarded-For from Nginx; fall back to remote addr.
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            int comma = fwd.indexOf(',');
            return truncate((comma == -1 ? fwd : fwd.substring(0, comma)).trim(), 45);
        }
        return truncate(req.getRemoteAddr(), 45);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    public record IssuedRefreshToken(UUID entityId, UUID sessionId, String rawToken, Instant expiresAt) {}

    public record RotationResult(User user, IssuedRefreshToken newToken) {}
}
