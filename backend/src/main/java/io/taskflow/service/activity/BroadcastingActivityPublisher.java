package io.taskflow.service.activity;

import io.taskflow.common.tenant.TenantContext;
import io.taskflow.domain.enums.ActivityType;
import io.taskflow.dto.event.BoardEvent;
import io.taskflow.websocket.BoardEventBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Decorates {@link DbActivityPublisher} with a post-commit WebSocket broadcast.
 *
 * <p><b>Why a decorator and not a service-layer call?</b> Adding broadcast logic to every
 * service would scatter the concern across the codebase. By making this the
 * {@link Primary} {@link ActivityPublisher}, <em>every</em> existing service from Step 3
 * (project/board/task/comment/org) automatically broadcasts with zero code changes.</p>
 *
 * <p><b>Why after-commit?</b> Broadcasting <em>inside</em> the transaction means subscribers
 * could react to an event whose DB write is still rolling back — a classic ghost-update
 * bug. Spring's {@link TransactionSynchronization#afterCommit()} fires only on success,
 * after the connection is returned to the pool.</p>
 *
 * <p>The event payload is computed eagerly (before commit) so we don't hold references
 * to entities that may be detached by the time afterCommit runs.</p>
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class BroadcastingActivityPublisher implements ActivityPublisher {

    private final DbActivityPublisher delegate;
    private final BoardEventBroadcaster broadcaster;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(ActivityType type,
                        UUID projectId,
                        UUID boardId,
                        UUID taskId,
                        Map<String, Object> payload) {
        // 1) Persist the activity row in the caller's TX (failure here rolls everything back).
        delegate.publish(type, projectId, boardId, taskId, payload);

        // 2) Capture the event eagerly — TenantContext is bound to this thread; we
        //    can't rely on it inside the afterCommit callback (different invocation context).
        BoardEvent event = new BoardEvent(
                type,
                TenantContext.requireOrganizationId(),
                projectId,
                boardId,
                taskId,
                TenantContext.requireUserId(),
                payload,
                MDC.get("traceId"),
                Instant.now()
        );

        // 3) Schedule fan-out for after the originating TX commits.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    broadcaster.broadcast(event);
                }
            });
        } else {
            // Defensive: should never happen because of Propagation.MANDATORY above.
            // If it does, broadcast inline — better than dropping the event silently.
            log.warn("No active transaction during activity publish; broadcasting inline");
            broadcaster.broadcast(event);
        }
    }
}
