package io.taskflow.dto.project;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        String key,
        String description,
        Instant createdAt,
        Instant updatedAt
) {}
