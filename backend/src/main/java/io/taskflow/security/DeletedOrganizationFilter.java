package io.taskflow.security;

import io.taskflow.common.tenant.TenantContext;
import io.taskflow.domain.Organization;
import io.taskflow.repository.OrganizationRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Blocks normal API usage when the active workspace is in trash. Trash restore/list
 * endpoints remain available so admins can recover the workspace.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 20)
@RequiredArgsConstructor
public class DeletedOrganizationFilter extends OncePerRequestFilter {

    private final OrganizationRepository organizations;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        var principal = TenantContext.get();
        if (principal != null && principal.organizationId() != null) {
            Organization org = organizations.findById(principal.organizationId()).orElse(null);
            if (org != null && org.isDeleted() && !isTrashOrAuthPath(request)) {
                response.sendError(HttpStatus.FORBIDDEN.value(),
                        "This workspace is in the trash. Restore it from Trash to continue.");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static boolean isTrashOrAuthPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/trash")
                || path.startsWith("/auth")
                || path.startsWith("/actuator");
    }
}
