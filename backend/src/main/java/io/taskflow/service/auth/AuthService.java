package io.taskflow.service.auth;

import io.taskflow.dto.auth.AuthResponse;
import io.taskflow.dto.auth.LoginRequest;
import io.taskflow.dto.auth.RegisterRequest;
import io.taskflow.security.RefreshTokenService.IssuedRefreshToken;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

/**
 * Auth-service interface. The interface is the API surface; the impl is replaceable
 * (e.g. for an OIDC-backed variant) without touching controllers.
 *
 * <p>Every method that issues a refresh token returns it via {@link Result} so the
 * controller can write the HttpOnly cookie. The raw token never appears in the JSON
 * response body.</p>
 */
public interface AuthService {

    Result register(RegisterRequest req, HttpServletRequest httpRequest);

    Result login(LoginRequest req, HttpServletRequest httpRequest);

    /**
     * @param presentedToken raw refresh token (from cookie)
     * @param targetOrgId    optional — pin the new access token to this org. If null,
     *                       keeps the user in the same org they were last in (best-effort
     *                       via the {@code orgId} hint from the SPA, or first membership).
     */
    Result refresh(String presentedToken, UUID targetOrgId, HttpServletRequest httpRequest);

    Result switchOrg(UUID currentUserId, UUID targetOrgId, HttpServletRequest httpRequest);

    void logout(UUID sessionId);

    /**
     * Carries both the JSON-body response and (when a new refresh token was issued)
     * the raw cookie value. The controller is the only piece that should ever see
     * the raw refresh token.
     */
    record Result(AuthResponse body, IssuedRefreshToken refreshToken) {}
}
