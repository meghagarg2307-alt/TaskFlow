package io.taskflow.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Contract for entities that support trash / restore. {@code isDeleted()} is true when
 * {@code deletedAt} is set — there is no separate {@code is_deleted} column.
 */
public interface SoftDeletable {

    Instant getDeletedAt();
    void setDeletedAt(Instant deletedAt);

    UUID getDeletedBy();
    void setDeletedBy(UUID deletedBy);

    Instant getRestoredAt();
    void setRestoredAt(Instant restoredAt);

    UUID getRestoredBy();
    void setRestoredBy(UUID restoredBy);

    default boolean isDeleted() {
        return getDeletedAt() != null;
    }
}
