package io.taskflow.domain;

import io.taskflow.domain.enums.TaskPriority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * The core unit of work. Tasks live in a column, but we also denormalize {@code board_id}
 * and {@code organization_id} onto the row because:
 *
 * <ol>
 *   <li>Multi-tenant safety: every query is filtered by {@code organization_id} —
 *       cheaper than joining through column→board→project just to enforce isolation.</li>
 *   <li>Board view fetch: {@code WHERE board_id = ?} hits a covering composite index
 *       {@code (board_id, column_id, position)} for the entire board render.</li>
 * </ol>
 *
 * <p>We accept the modest write-time cost of keeping these in sync when a task is
 * moved (which is rare relative to reads).</p>
 *
 * <p>{@code @Version} prevents lost updates when two collaborators edit the same task
 * concurrently — the second write throws {@code OptimisticLockException} and the
 * client re-fetches.</p>
 */
@Entity
@Table(name = "tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task extends BaseEntity implements SoftDeletable {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "board_id", nullable = false)
    private Board board;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "column_id", nullable = false)
    private BoardColumn column;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    @Builder.Default
    private TaskPriority priority = TaskPriority.MEDIUM;

    @Column(name = "position", nullable = false)
    private long position;

    @Column(name = "due_date")
    private Instant dueDate;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by", columnDefinition = "uuid")
    private UUID deletedBy;

    @Column(name = "restored_at")
    private Instant restoredAt;

    @Column(name = "restored_by", columnDefinition = "uuid")
    private UUID restoredBy;
}
