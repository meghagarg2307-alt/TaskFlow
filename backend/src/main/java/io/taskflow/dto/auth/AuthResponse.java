package io.taskflow.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Response body for {@code /auth/login}, {@code /auth/refresh}, {@code /auth/switch-org}.
 *
 * <p>The refresh token is <em>never</em> in the body — it rides in an HttpOnly cookie.
 * Putting it in the body would defeat the cookie's XSS protection.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(
        String accessToken,
        Instant accessTokenExpiresAt,
        UserSummary user,
        OrganizationSummary activeOrganization,
        List<OrganizationSummary> organizations
) {}
