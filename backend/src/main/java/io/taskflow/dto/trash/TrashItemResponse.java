package io.taskflow.dto.trash;

import io.taskflow.domain.enums.TrashResourceType;

import java.time.Instant;
import java.util.UUID;

public record TrashItemResponse(
        TrashResourceType resourceType,
        UUID id,
        String name,
        String description,
        Instant deletedAt,
        UUID deletedBy,
        String deletedByName,
        int daysUntilPermanentDeletion,
        UUID parentId,
        String parentName
) {}
