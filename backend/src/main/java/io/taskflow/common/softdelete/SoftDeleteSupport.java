package io.taskflow.common.softdelete;

import io.taskflow.domain.SoftDeletable;

import java.time.Instant;
import java.util.UUID;

public final class SoftDeleteSupport {

    private SoftDeleteSupport() {}

    public static void markDeleted(SoftDeletable entity, UUID deletedBy, Instant when) {
        entity.setDeletedAt(when);
        entity.setDeletedBy(deletedBy);
        entity.setRestoredAt(null);
        entity.setRestoredBy(null);
    }

    public static void markDeleted(SoftDeletable entity, UUID deletedBy) {
        markDeleted(entity, deletedBy, Instant.now());
    }

    public static void markRestored(SoftDeletable entity, UUID restoredBy) {
        entity.setDeletedAt(null);
        entity.setDeletedBy(null);
        entity.setRestoredAt(Instant.now());
        entity.setRestoredBy(restoredBy);
    }
}
