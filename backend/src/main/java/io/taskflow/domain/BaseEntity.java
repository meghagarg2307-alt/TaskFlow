package io.taskflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Shared identity + auditing columns for every persisted entity.
 *
 * <p><b>UUID PK over auto-increment</b>: in a multi-tenant SaaS, IDs are exposed in
 * URLs (boards/tasks). Sequential IDs leak business metrics (org size, growth rate)
 * and enable enumeration attacks. UUIDv4 also lets the client mint IDs locally for
 * optimistic UI without a server round-trip.</p>
 *
 * <p><b>equals/hashCode on UUID, not entity reference</b>: Hibernate proxies break
 * default {@code Object.equals}. We compare by UUID and use {@code getClass()} for
 * cheap-but-correct equality across the JPA proxy boundary.</p>
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", columnDefinition = "uuid", updatable = false)
    private UUID createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", columnDefinition = "uuid")
    private UUID updatedBy;

    @PrePersist
    private void ensureId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BaseEntity that = (BaseEntity) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        // Constant hashCode is the correct choice for entities whose id is generated
        // on persist — protects against breakage when the entity transitions from
        // transient to persistent inside a HashSet. See Vlad Mihalcea's writeup.
        return Objects.hash(getClass());
    }
}
