package io.taskflow.service.activity;

import io.taskflow.domain.enums.ActivityType;

import java.util.Map;
import java.util.UUID;

/**
 * Single seam every business operation calls to record an activity. Step 3 implements
 * <em>DB-only</em> publishing (single transaction with the originating change). Step 4
 * extends this seam to also fan out a WebSocket event; Step 5 publishes to Redis so
 * <em>every</em> backend instance can broadcast to its connected clients.
 *
 * <p>Implementations <b>must</b> be safe to call inside the originating transaction,
 * since we want the activity row to commit (or roll back) atomically with the change
 * that produced it. No "fire and forget" semantics at this layer.</p>
 */
public interface ActivityPublisher {

    /**
     * @param type        what happened
     * @param projectId   optional — populated when scoped to a project
     * @param boardId     optional — populated when scoped to a board (drives WS topic)
     * @param taskId      optional — populated when scoped to a task
     * @param payload     event-specific JSON payload (e.g. old/new column ids for TASK_MOVED)
     */
    void publish(ActivityType type,
                 UUID projectId,
                 UUID boardId,
                 UUID taskId,
                 Map<String, Object> payload);
}
