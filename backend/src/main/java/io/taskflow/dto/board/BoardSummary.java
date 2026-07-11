package io.taskflow.dto.board;

import java.time.Instant;
import java.util.UUID;

public record BoardSummary(
        UUID id,
        UUID projectId,
        String name,
        String description,
        long version,
        Instant createdAt,
        Instant updatedAt
) {}
