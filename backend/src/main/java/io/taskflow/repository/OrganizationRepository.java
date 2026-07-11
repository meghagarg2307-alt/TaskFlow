package io.taskflow.repository;

import io.taskflow.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    Optional<Organization> findBySlug(String slug);
    boolean existsBySlug(String slug);

    @Query("select o from Organization o where o.deletedAt is not null and o.deletedAt < :cutoff")
    List<Organization> findDeletedBefore(Instant cutoff);
}
