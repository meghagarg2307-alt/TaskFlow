package io.taskflow.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * Typed binding for {@code taskflow.security.*}. Validation runs at startup — a
 * misconfigured production deployment fails fast at boot instead of at first request.
 */
@Validated
@ConfigurationProperties(prefix = "taskflow.security")
public record SecurityProperties(
        @NotNull Jwt jwt,
        @NotNull Cors cors,
        @NotNull Cookie cookie
) {
    public record Jwt(
            @NotBlank String issuer,
            @NotBlank String secret,
            @NotNull Duration accessTokenTtl,
            @NotNull Duration refreshTokenTtl
    ) {}

    public record Cors(
            @NotNull List<String> allowedOrigins
    ) {}

    /**
     * Cookie attributes for the refresh token. {@code secure} must be true in
     * production (cookie won't be sent over HTTP). {@code sameSite} should remain
     * {@code Strict} unless the SPA is on a different site, in which case use {@code Lax}.
     */
    public record Cookie(
            boolean secure,
            @NotBlank String sameSite
    ) {}
}
