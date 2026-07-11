package io.taskflow.domain;

import io.taskflow.domain.enums.OrganizationRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Join entity (User × Organization) carrying the user's role in that organization.
 *
 * <p>We model this as an entity (not a {@code @ManyToMany} with join table) because
 * the relationship carries data (role, joined_at) and is queried directly when
 * resolving permissions at login.</p>
 *
 * <p>The unique constraint on {@code (user_id, organization_id)} prevents duplicate
 * memberships — also enforced at the SQL level in V1 migration.</p>
 */
@Entity
@Table(
        name = "organization_memberships",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_membership_user_org",
                columnNames = {"user_id", "organization_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationMembership extends BaseEntity {

    // Eager-fetch on a small, always-needed reference is fine and avoids N+1 surprises
    // in the membership lookup path. The User and Organization rows are cached anyway.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_membership_user"))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_membership_org"))
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private OrganizationRole role;
}
