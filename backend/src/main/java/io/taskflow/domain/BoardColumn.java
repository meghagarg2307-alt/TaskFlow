package io.taskflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A column within a board (e.g. "To Do", "In Progress", "Done"). Columns are
 * board-owned; they don't exist outside one. Named {@code BoardColumn} (not
 * {@code Column}) to avoid clashing with {@link jakarta.persistence.Column}.
 *
 * <p>{@code position} uses a 64-bit gap-based scheme: when inserting between two
 * neighbors with positions A and B, we use {@code (A + B) / 2}. This gives ~63
 * reorders before we hit precision exhaustion, after which a background job
 * rebalances the column. Same scheme is used for tasks within a column.</p>
 */
@Entity
@Table(name = "board_columns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoardColumn extends BaseEntity implements SoftDeletable {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "board_id", nullable = false, updatable = false)
    private Board board;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "position", nullable = false)
    private long position;

    /** Optional WIP limit (work-in-progress) — null means no limit. */
    @Column(name = "wip_limit")
    private Integer wipLimit;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by", columnDefinition = "uuid")
    private UUID deletedBy;

    @Column(name = "restored_at")
    private Instant restoredAt;

    @Column(name = "restored_by", columnDefinition = "uuid")
    private UUID restoredBy;
}
