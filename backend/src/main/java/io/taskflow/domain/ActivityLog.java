package io.taskflow.domain;

import io.taskflow.domain.enums.ActivityType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

/**
 * Append-only activity feed. Indexed on {@code (organization_id, created_at DESC)} to
 * support the org-wide feed; additional indexes on {@code board_id} and {@code task_id}
 * support per-board and per-task feeds.
 *
 * <p>The {@code payload} jsonb column stores type-specific data (e.g. old/new column
 * for {@code TASK_MOVED}). Storing it as jsonb (not separate columns) means we can add
 * new {@link ActivityType}s without schema changes.</p>
 *
 * <p>Partitioning note: at scale ({@literal >}10M rows), this table should be range-
 * partitioned by {@code created_at} (monthly). The current schema makes that a
 * non-breaking change.</p>
 */
@Entity
@Table(name = "activity_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 32, updatable = false)
    private ActivityType activityType;

    /** Nullable — not every activity is scoped to a board (e.g. MEMBER_INVITED). */
    @Column(name = "board_id", columnDefinition = "uuid", updatable = false)
    private UUID boardId;

    @Column(name = "task_id", columnDefinition = "uuid", updatable = false)
    private UUID taskId;

    @Column(name = "project_id", columnDefinition = "uuid", updatable = false)
    private UUID projectId;

    /**
     * Free-form event payload. Kept open-ended so we can evolve event shape without
     * migrations — readers should treat unknown keys as forward-compatible additions.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private Map<String, Object> payload;
}
