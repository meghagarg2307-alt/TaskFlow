package io.taskflow.common.tenant;

import io.taskflow.domain.enums.OrganizationRole;

import java.util.UUID;

/**
 * Immutable snapshot of the authenticated user's identity within the active tenant.
 *
 * <p>This is the value bound to {@link TenantContext} on every authenticated request.
 * It carries only the minimum needed for authorization decisions: the user's id, the
 * organization they are acting in, and their role in that organization. Anything more
 * (email, name, etc.) should be loaded on demand to keep the JWT small.</p>
 */
public record TenantPrincipal(
        UUID userId,
        UUID organizationId,
        OrganizationRole role
) {
    public boolean isAdmin() {
        return role == OrganizationRole.ADMIN;
    }

    public boolean isAtLeastManager() {
        return role == OrganizationRole.ADMIN || role == OrganizationRole.MANAGER;
    }
}
