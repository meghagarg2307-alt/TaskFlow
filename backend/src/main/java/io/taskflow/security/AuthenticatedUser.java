package io.taskflow.security;

import io.taskflow.common.tenant.TenantPrincipal;
import io.taskflow.domain.enums.OrganizationRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Spring Security {@code Authentication} principal — what
 * {@code SecurityContextHolder.getContext().getAuthentication().getPrincipal()} returns
 * on authenticated requests. Also produces the {@link TenantPrincipal} that is bound
 * to {@link io.taskflow.common.tenant.TenantContext}.
 *
 * <p>Authorities are derived from the org role: {@code ROLE_ADMIN}, {@code ROLE_MANAGER},
 * {@code ROLE_MEMBER}. {@code @PreAuthorize("hasRole('MANAGER')")} works out of the box.</p>
 */
public record AuthenticatedUser(
        UUID userId,
        UUID organizationId,
        OrganizationRole role,
        String tokenId
) {

    public TenantPrincipal toTenantPrincipal() {
        return new TenantPrincipal(userId, organizationId, role);
    }

    public Collection<? extends GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}
