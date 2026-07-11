package io.taskflow.dto.activity;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.taskflow.domain.enums.ActivityType;
import io.taskflow.dto.common.UserRef;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ActivityResponse(
        UUID id,
        ActivityType type,
        UserRef actor,
        UUID projectId,
        UUID boardId,
        UUID taskId,
        Map<String, Object> payload,
        Instant createdAt
) {}
