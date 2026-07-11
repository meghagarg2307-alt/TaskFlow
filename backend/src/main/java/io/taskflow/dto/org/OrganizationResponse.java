package io.taskflow.dto.org;

import java.time.Instant;
import java.util.UUID;

public record OrganizationResponse(
        UUID id,
        String name,
        String slug,
        String description,
        Instant createdAt,
        Instant updatedAt
) {}
