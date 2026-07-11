package io.taskflow.controller;

import io.taskflow.common.tenant.TenantContext;
import io.taskflow.config.SecurityProperties;
import io.taskflow.dto.auth.AuthResponse;
import io.taskflow.dto.auth.LoginRequest;
import io.taskflow.dto.auth.RegisterRequest;
import io.taskflow.dto.auth.SwitchOrgRequest;
import io.taskflow.exception.TooManyRequestsException;
import io.taskflow.exception.UnauthorizedException;
import io.taskflow.security.RefreshTokenService;
import io.taskflow.security.RefreshTokenService.IssuedRefreshToken;
import io.taskflow.service.auth.AuthService;
import io.taskflow.service.auth.AuthService.Result;
import io.taskflow.service.ratelimit.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Authentication endpoints. All write the access token + user/org info into the JSON
 * body, and the refresh token into an HttpOnly cookie scoped to {@code /auth} so it
 * is only ever sent to this controller.
 *
 * <p><b>Cookie attributes</b>:</p>
 * <ul>
 *   <li>{@code HttpOnly} — inaccessible to JS, neutralizes XSS theft</li>
 *   <li>{@code Secure} — TLS-only (relaxed in dev via {@code app.cookie.secure=false})</li>
 *   <li>{@code SameSite=Strict} — neutralizes CSRF; the SPA is same-site under Nginx</li>
 *   <li>{@code Path=/auth} — refresh cookie is not sent on REST/WS endpoints, reducing
 *       its exposure surface</li>
 * </ul>
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_COOKIE = "tf_refresh";
    private static final String COOKIE_PATH    = "/auth";

    /** Rate-limit windows. Conservative defaults — easy to tune later via config. */
    private static final Duration LOGIN_WINDOW = Duration.ofMinutes(15);
    private static final long LOGIN_LIMIT_PER_EMAIL = 5;     // brute force on one user
    private static final long LOGIN_LIMIT_PER_IP    = 20;    // credential-stuffing from one source
    private static final Duration REGISTER_WINDOW = Duration.ofHours(1);
    private static final long REGISTER_LIMIT_PER_IP = 10;    // spam signup defense

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final RateLimiter rateLimiter;
    private final SecurityProperties securityProperties;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req,
                                                 HttpServletRequest http) {
        enforce("register:ip:" + clientIp(http), REGISTER_LIMIT_PER_IP, REGISTER_WINDOW,
                "TOO_MANY_REGISTRATIONS",
                "Too many sign-up attempts from this network. Try again later.");
        return toResponse(authService.register(req, http));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req,
                                              HttpServletRequest http) {
        // Both throttles must pass — protects a single account from being targeted AND
        // protects the system from credential-stuffing from a single source.
        enforce("login:email:" + req.email().toLowerCase(), LOGIN_LIMIT_PER_EMAIL, LOGIN_WINDOW,
                "TOO_MANY_LOGIN_ATTEMPTS",
                "Too many login attempts. Try again in a few minutes.");
        enforce("login:ip:" + clientIp(http), LOGIN_LIMIT_PER_IP, LOGIN_WINDOW,
                "TOO_MANY_LOGIN_ATTEMPTS",
                "Too many login attempts from this network. Try again later.");
        return toResponse(authService.login(req, http));
    }

    /**
     * Trades the refresh cookie for a fresh access token (and a rotated refresh cookie).
     * The optional {@code orgId} query param lets the SPA pin a specific org context;
     * otherwise we default to the user's first membership.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshCookie,
            @RequestParam(name = "orgId", required = false) UUID orgId,
            HttpServletRequest http) {

        if (refreshCookie == null || refreshCookie.isBlank()) {
            throw new UnauthorizedException("NO_REFRESH_COOKIE", "Missing refresh token");
        }
        return toResponse(authService.refresh(refreshCookie, orgId, http));
    }

    /**
     * Switch the active organization. Requires a valid access token (so we know who
     * the user is); does not rotate the refresh token.
     */
    @PostMapping("/switch-org")
    public ResponseEntity<AuthResponse> switchOrg(@Valid @RequestBody SwitchOrgRequest req,
                                                  HttpServletRequest http) {
        UUID userId = TenantContext.requireUserId();
        return toResponse(authService.switchOrg(userId, req.organizationId(), http));
    }

    /**
     * Logout: revoke the entire session family + clear the cookie. Idempotent — calling
     * twice or without a valid cookie still returns 204.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String refreshCookie) {
        refreshTokenService.revokeByPresentedToken(refreshCookie);
        ResponseCookie clear = ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true)
                .secure(securityProperties.cookie().secure())
                .sameSite(securityProperties.cookie().sameSite())
                .path(COOKIE_PATH)
                .maxAge(0)
                .build();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clear.toString())
                .build();
    }

    // -------------------------------------------------------- internals

    private ResponseEntity<AuthResponse> toResponse(Result result) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (result.refreshToken() != null) {
            builder.header(HttpHeaders.SET_COOKIE, buildCookie(result.refreshToken()).toString());
        }
        return builder.body(result.body());
    }

    private ResponseCookie buildCookie(IssuedRefreshToken token) {
        long maxAgeSeconds = Duration.between(Instant.now(), token.expiresAt()).getSeconds();
        return ResponseCookie.from(REFRESH_COOKIE, token.rawToken())
                .httpOnly(true)
                .secure(securityProperties.cookie().secure())
                .sameSite(securityProperties.cookie().sameSite())
                .path(COOKIE_PATH)
                .maxAge(Math.max(0, maxAgeSeconds))
                .build();
    }

    private void enforce(String key, long limit, Duration window, String code, String message) {
        if (!rateLimiter.tryAcquire(key, limit, window)) {
            throw new TooManyRequestsException(code, message, window);
        }
    }

    /** Honor X-Forwarded-For from Nginx; fall back to remote addr. */
    private static String clientIp(HttpServletRequest req) {
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            int comma = fwd.indexOf(',');
            return (comma == -1 ? fwd : fwd.substring(0, comma)).trim();
        }
        return req.getRemoteAddr();
    }
}
