package io.taskflow.common.tenant;

import java.util.UUID;

/**
 * Thread-local holder for the currently authenticated tenant (organization) and user.
 *
 * <p>Populated by {@code JwtAuthenticationFilter} on the request thread, and propagated
 * to virtual threads automatically (ThreadLocal works on virtual threads).</p>
 *
 * <p><b>Why a ThreadLocal instead of passing IDs through method signatures?</b><br>
 * Tenant ID is a cross-cutting concern needed by repositories, services, the auditor,
 * the websocket fan-out, and the activity logger. Threading it through every method
 * signature would pollute every API. We treat it the same way Spring Security treats
 * {@code Authentication}: a request-scoped context the framework owns.</p>
 *
 * <p><b>Safety:</b> the security filter <em>must</em> call {@link #clear()} in a finally
 * block after the request completes to avoid leaks across pooled threads. For virtual
 * threads (one-shot, not pooled) this matters less, but we still clear for hygiene.</p>
 */
public final class TenantContext {

    private static final ThreadLocal<TenantPrincipal> CONTEXT = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(TenantPrincipal principal) {
        CONTEXT.set(principal);
    }

    public static TenantPrincipal get() {
        return CONTEXT.get();
    }

    /**
     * @return the active organization id; throws if no tenant is bound — fail-fast
     *         is preferable to silently leaking data across tenants.
     */
    public static UUID requireOrganizationId() {
        TenantPrincipal p = CONTEXT.get();
        if (p == null || p.organizationId() == null) {
            throw new IllegalStateException("No tenant bound to current thread");
        }
        return p.organizationId();
    }

    public static UUID requireUserId() {
        TenantPrincipal p = CONTEXT.get();
        if (p == null || p.userId() == null) {
            throw new IllegalStateException("No user bound to current thread");
        }
        return p.userId();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
