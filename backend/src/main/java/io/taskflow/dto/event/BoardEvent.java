package io.taskflow.dto.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.taskflow.domain.enums.ActivityType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Real-time event envelope published over STOMP. Same shape regardless of topic.
 *
 * <p>The {@code payload} mirrors the corresponding {@code ActivityLog.payload}, so
 * the SPA can apply the same delta logic for both initial activity-feed renders and
 * live updates.</p>
 *
 * <p>{@code traceId} lets the SPA correlate a self-originated REST call with the
 * WS echo, so the optimistic-UI layer can drop its own echo and avoid double-applying.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BoardEvent(
        ActivityType type,
        UUID organizationId,
        UUID projectId,
        UUID boardId,
        UUID taskId,
        UUID actorId,
        Map<String, Object> payload,
        String traceId,
        Instant timestamp
) {}
