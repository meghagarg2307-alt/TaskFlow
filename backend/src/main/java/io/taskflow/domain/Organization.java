package io.taskflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * The tenant. Every row in projects/boards/tasks/etc. carries a foreign key here,
 * and every query in the service layer filters by it.
 *
 * <p>{@code slug} is the URL-safe identifier (e.g. {@code /acme/boards/123}) — must be
 * globally unique and is what users share. {@code name} is the display name.</p>
 */
@Entity
@Table(name = "organizations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Organization extends BaseEntity implements SoftDeletable {

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 64)
    private String slug;

    @Column(name = "description", length = 1000)
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
