package io.taskflow.dto.comment;

import io.taskflow.dto.common.UserRef;

import java.time.Instant;
import java.util.UUID;

public record CommentResponse(
        UUID id,
        UUID taskId,
        UserRef author,
        String body,
        Instant createdAt
) {}
