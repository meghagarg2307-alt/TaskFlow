package io.taskflow.dto.task;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.taskflow.domain.enums.TaskPriority;
import io.taskflow.dto.common.UserRef;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskResponse(
        UUID id,
        UUID boardId,
        UUID columnId,
        String title,
        String description,
        TaskPriority priority,
        long position,
        UserRef assignee,
        Instant dueDate,
        long version,
        Instant createdAt,
        Instant updatedAt
) {}
