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
 * A project groups related boards within an organization. We do not model
 * "members of a project" separately in v1 — visibility is at the org level. This is a
 * deliberate scope choice: per-project ACLs are a significant feature addition that
 * belongs in a later iteration.
 *
 * <p>Soft-deleted via {@code deleted_at}: deleting a project hides it from listings
 * but preserves history for the activity feed and any restore feature.</p>
 */
@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project extends BaseEntity implements SoftDeletable {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, updatable = false)
    private Organization organization;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "key", nullable = false, length = 10)
    private String key;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by", columnDefinition = "uuid")
    private UUID deletedBy;

    @Column(name = "restored_at")
    private Instant restoredAt;

    @Column(name = "restored_by", columnDefinition = "uuid")
    private UUID restoredBy;
}
