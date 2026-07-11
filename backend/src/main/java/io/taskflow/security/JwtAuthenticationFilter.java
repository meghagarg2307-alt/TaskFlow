package io.taskflow.security;

import io.taskflow.common.tenant.TenantContext;
import io.taskflow.exception.UnauthorizedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;

/**
 * Resolves the {@code Authorization: Bearer <jwt>} header, validates the access token,
 * and populates both Spring Security's {@link SecurityContextHolder} <em>and</em> our
 * {@link TenantContext}. Also sets MDC keys ({@code tenantId}, {@code userId},
 * {@code traceId}) so every log line on the request thread is auto-tagged.
 *
 * <p><b>Why a separate filter (not Spring's BearerToken support)?</b> We carry two
 * extra claims (org + role) that Spring's default OAuth2 resource-server flow doesn't
 * know about, and we need to bind tenant context for our service layer. A small
 * focused filter is clearer than configuring custom converters around the stock one.</p>
 *
 * <p>This filter is non-failing for missing/blank Authorization headers — the
 * downstream {@code AuthorizationFilter} decides whether the endpoint is public or not.
 * It only short-circuits with 401 for tokens that are present but invalid.</p>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final JwtAuthenticationEntryPoint entryPoint;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            chain.doFilter(request, response);
            return;
        }
        String token = header.substring(BEARER.length()).trim();
        if (token.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        try {
            JwtService.AccessTokenClaims claims = jwtService.parseAccessToken(token);
            AuthenticatedUser principal = new AuthenticatedUser(
                    claims.userId(), claims.organizationId(), claims.role(), claims.jti());

            Authentication auth = new Authentication(principal, principal.authorities());
            SecurityContextHolder.getContext().setAuthentication(auth);
            TenantContext.set(principal.toTenantPrincipal());

            MDC.put("userId",   principal.userId().toString());
            MDC.put("tenantId", principal.organizationId().toString());

            chain.doFilter(request, response);

        } catch (UnauthorizedException ex) {
            // Token was present but invalid — fail loud, don't fall through to "anonymous".
            SecurityContextHolder.clearContext();
            entryPoint.commenceWithCode(request, response, ex.getErrorCode(), ex.getMessage());
        } finally {
            // Hygiene: virtual threads aren't pooled, but request threads in tests may be.
            SecurityContextHolder.clearContext();
            TenantContext.clear();
            MDC.remove("userId");
            MDC.remove("tenantId");
        }
    }

    /** Thin Spring Security Authentication wrapper around {@link AuthenticatedUser}. */
    public static final class Authentication extends AbstractAuthenticationToken {
        private final AuthenticatedUser principal;

        public Authentication(AuthenticatedUser principal,
                              Collection<? extends org.springframework.security.core.GrantedAuthority> auths) {
            super(auths);
            this.principal = principal;
            setAuthenticated(true);
        }

        @Override public Object getCredentials() { return null; }
        @Override public AuthenticatedUser getPrincipal() { return principal; }
    }
}
