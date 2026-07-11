package io.taskflow.dto.task;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.taskflow.domain.enums.TaskPriority;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Patch-style task update. We use {@link Optional} on {@code assigneeId} and
 * {@code dueDate} so the SPA can distinguish "leave alone" (absent / null) from
 * "clear it" (empty Optional). This is a common JSON-PATCH-lite pattern; Jackson
 * deserializes {@code "assigneeId": null} as {@code Optional.empty()} and a missing
 * field as Java null. See {@link UpdateTaskRequestDeserializer} for the implementation.
 */
@JsonDeserialize(using = UpdateTaskRequestDeserializer.class)
public record UpdateTaskRequest(
        @Size(min = 1, max = 300) String title,
        @Size(max = 16_000) String description,
        TaskPriority priority,
        Optional<UUID> assigneeId,
        Optional<Instant> dueDate,
        Long expectedVersion
) {}
