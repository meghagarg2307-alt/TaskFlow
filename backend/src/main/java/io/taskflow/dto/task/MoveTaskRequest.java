package io.taskflow.dto.task;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Drag-and-drop move. The client sends the <em>neighbors</em> in the target column —
 * the server is the source of truth for positions. This is the canonical pattern used
 * by Trello/Linear and keeps the client trivial: it just identifies the two tasks the
 * dragged card was dropped between (either may be null at the boundaries).
 *
 * <p>The server will:</p>
 * <ol>
 *   <li>Validate the target column belongs to the same org &amp; same board</li>
 *   <li>Validate the neighbors are in the target column</li>
 *   <li>Compute a new position via {@link io.taskflow.common.order.PositionCalculator}</li>
 *   <li>Apply with optimistic-lock check ({@code expectedVersion})</li>
 * </ol>
 */
public record MoveTaskRequest(
        @NotNull UUID targetColumnId,
        UUID beforeTaskId,
        UUID afterTaskId,
        @NotNull Long expectedVersion
) {}
