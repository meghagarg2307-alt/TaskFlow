package io.taskflow.dto.task;

import io.taskflow.domain.enums.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record CreateTaskRequest(
        @NotNull UUID columnId,
        @NotBlank @Size(min = 1, max = 300) String title,
        @Size(max = 16_000) String description,
        TaskPriority priority,
        UUID assigneeId,
        Instant dueDate
) {}
