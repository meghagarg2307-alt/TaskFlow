package io.taskflow.dto.board;

import io.taskflow.dto.task.TaskResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full payload to render a board in one network round-trip. The SPA hydrates its store
 * from this and then keeps it warm via WebSocket deltas (Step 4).
 *
 * <p>Tasks are returned <em>flat</em> with a {@code columnId} reference rather than
 * nested into columns. This keeps the wire format normalized — easier to apply
 * incremental WS updates to a flat map than to mutate nested arrays.</p>
 */
public record BoardSnapshot(
        UUID id,
        UUID projectId,
        String name,
        String description,
        long version,
        Instant updatedAt,
        List<ColumnView> columns,
        List<TaskResponse> tasks
) {
    public record ColumnView(
            UUID id,
            String name,
            long position,
            Integer wipLimit
    ) {}
}
