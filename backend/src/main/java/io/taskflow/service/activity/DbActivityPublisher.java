package io.taskflow.service.activity;

import io.taskflow.common.tenant.TenantContext;
import io.taskflow.domain.ActivityLog;
import io.taskflow.domain.Organization;
import io.taskflow.domain.User;
import io.taskflow.domain.enums.ActivityType;
import io.taskflow.repository.ActivityLogRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Default {@link ActivityPublisher}: writes a row to {@code activity_log} in the
 * caller's transaction.
 *
 * <p>Uses {@link EntityManager#getReference} to attach the {@code organization} and
 * {@code actor} <em>without</em> issuing a SELECT — this is a hot write path; we
 * already know the ids from the tenant context. Hibernate uses the proxy to populate
 * the FK and never dereferences the entity.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DbActivityPublisher implements ActivityPublisher {

    private final ActivityLogRepository repository;
    private final EntityManager entityManager;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(ActivityType type,
                        UUID projectId,
                        UUID boardId,
                        UUID taskId,
                        Map<String, Object> payload) {
        UUID orgId  = TenantContext.requireOrganizationId();
        UUID userId = TenantContext.requireUserId();

        ActivityLog row = ActivityLog.builder()
                .organization(entityManager.getReference(Organization.class, orgId))
                .actor(entityManager.getReference(User.class, userId))
                .activityType(type)
                .projectId(projectId)
                .boardId(boardId)
                .taskId(taskId)
                .payload(payload)
                .build();
        repository.save(row);
    }
}
