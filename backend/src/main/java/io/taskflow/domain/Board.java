package io.taskflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * A board lives inside a project and contains columns + tasks. It's the unit of
 * real-time collaboration: WebSocket topics are scoped per board
 * ({@code /topic/boards/{boardId}}).
 *
 * <p>{@code @Version} enables optimistic locking — important because multiple users
 * can edit a board's structure (e.g. reorder columns) concurrently. JPA throws
 * {@code OptimisticLockException} on stale writes, which our global handler converts
 * into a 409 with an "out-of-date, please refresh" payload.</p>
 */
@Entity
@Table(name = "boards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Board extends BaseEntity implements SoftDeletable {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 2000)
    private String description;

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
