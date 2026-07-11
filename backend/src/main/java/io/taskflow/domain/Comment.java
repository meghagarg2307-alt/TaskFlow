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
 * Threaded comments are out of scope for v1 — flat list ordered by created_at. A
 * {@code parent_comment_id} self-reference can be added in a non-breaking migration
 * later.
 *
 * <p>Soft-deleted so the activity feed ("user X deleted a comment") remains coherent.</p>
 */
@Entity
@Table(name = "comments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comment extends BaseEntity implements SoftDeletable {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false, updatable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private User author;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by", columnDefinition = "uuid")
    private UUID deletedBy;

    @Column(name = "restored_at")
    private Instant restoredAt;

    @Column(name = "restored_by", columnDefinition = "uuid")
    private UUID restoredBy;
}
