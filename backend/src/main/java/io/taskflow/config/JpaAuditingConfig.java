package io.taskflow.config;

import io.taskflow.common.tenant.TenantContext;
import io.taskflow.common.tenant.TenantPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;

import java.util.Optional;
import java.util.UUID;

/**
 * Wires {@link AuditorAware} to the current {@link TenantPrincipal}, so every
 * {@code @CreatedBy}/{@code @LastModifiedBy} column is populated automatically.
 *
 * <p>Returning {@link Optional#empty()} for system writes (no authenticated user, e.g.
 * scheduled jobs) is intentional — JPA will leave the column null rather than failing,
 * which Flyway-defined NOT NULL constraints would otherwise reject for system writes.</p>
 */
@Configuration
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<UUID> auditorAware() {
        return () -> {
            TenantPrincipal p = TenantContext.get();
            return p == null ? Optional.empty() : Optional.of(p.userId());
        };
    }
}
