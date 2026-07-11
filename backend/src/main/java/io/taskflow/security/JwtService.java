package io.taskflow.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.taskflow.config.SecurityProperties;
import io.taskflow.domain.enums.OrganizationRole;
import io.taskflow.exception.UnauthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and verifies <b>access tokens</b> (compact JWTs, HS256).
 *
 * <p><b>Why JWT for access only:</b> JWTs are stateless — perfect for short-lived
 * (15 min) access tokens that don't need server lookup on every request. Refresh
 * tokens are <em>opaque random strings</em> stored hashed in Postgres, because we
 * need server-side revocation for them (see {@link RefreshTokenService}).</p>
 *
 * <p><b>Claims:</b></p>
 * <ul>
 *   <li>{@code sub} → user id (UUID)</li>
 *   <li>{@code org} → organization id (UUID) the token is currently scoped to</li>
 *   <li>{@code role} → user's role in that org</li>
 *   <li>{@code typ} → "access" (refresh tokens are <b>not</b> JWTs, so this is a tripwire
 *       if anyone ever tries to misuse one)</li>
 *   <li>{@code iss}, {@code iat}, {@code exp}, {@code jti}</li>
 * </ul>
 */
@Slf4j
@Service
public class JwtService {

    private static final String CLAIM_ORG  = "org";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYP  = "typ";
    private static final String TYP_ACCESS = "access";

    private final SecurityProperties props;
    private final SecretKey signingKey;

    public JwtService(SecurityProperties props) {
        this.props = props;
        this.signingKey = buildKey(props.jwt().secret());
    }

    /**
     * Accept the secret as either base64 or raw text. HS256 requires {@literal >=}256 bits;
     * we enforce that explicitly so a typo doesn't silently weaken the system.
     */
    private static SecretKey buildKey(String secret) {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (IllegalArgumentException notBase64) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "JWT secret must be at least 256 bits (32 bytes). Got " + keyBytes.length);
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public IssuedAccessToken issueAccessToken(UUID userId, UUID organizationId, OrganizationRole role) {
        Instant now = Instant.now();
        Instant exp = now.plus(props.jwt().accessTokenTtl());
        String jti = UUID.randomUUID().toString();

        String token = Jwts.builder()
                .issuer(props.jwt().issuer())
                .subject(userId.toString())
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claim(CLAIM_TYP, TYP_ACCESS)
                .claim(CLAIM_ORG, organizationId.toString())
                .claim(CLAIM_ROLE, role.name())
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();

        return new IssuedAccessToken(token, exp);
    }

    /**
     * Parses, validates signature/expiry/issuer/type, and returns claims.
     * Throws {@link UnauthorizedException} for any failure mode — the filter
     * translates this to a 401 without leaking which check failed.
     */
    public AccessTokenClaims parseAccessToken(String token) {
        try {
            Claims c = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(props.jwt().issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!TYP_ACCESS.equals(c.get(CLAIM_TYP, String.class))) {
                throw new UnauthorizedException("INVALID_TOKEN_TYPE", "Wrong token type");
            }
            UUID userId  = UUID.fromString(c.getSubject());
            UUID orgId   = UUID.fromString(c.get(CLAIM_ORG, String.class));
            OrganizationRole role = OrganizationRole.valueOf(c.get(CLAIM_ROLE, String.class));
            return new AccessTokenClaims(userId, orgId, role, c.getId(), c.getExpiration().toInstant());

        } catch (JwtException | IllegalArgumentException ex) {
            // Single coarse error - never tell the attacker which check failed
            log.debug("Access token rejected: {}", ex.getMessage());
            throw new UnauthorizedException("INVALID_TOKEN", "Invalid or expired token");
        }
    }

    public record IssuedAccessToken(String token, Instant expiresAt) {}

    public record AccessTokenClaims(UUID userId, UUID organizationId,
                                    OrganizationRole role, String jti, Instant expiresAt) {}
}
