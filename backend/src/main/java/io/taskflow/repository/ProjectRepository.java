package io.taskflow.repository;

import io.taskflow.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    @Query("""
            select p from Project p
             where p.organization.id = :organizationId
               and p.deletedAt is null
             order by p.createdAt desc
            """)
    List<Project> findAllActive(UUID organizationId);

    @Query("""
            select p from Project p
             where p.id = :id
               and p.organization.id = :organizationId
               and p.deletedAt is null
            """)
    Optional<Project> findActiveByIdAndOrg(UUID id, UUID organizationId);

    boolean existsByOrganization_IdAndKeyIgnoreCase(UUID organizationId, String key);

    @Query("""
            select p from Project p
             where p.organization.id = :organizationId
               and p.deletedAt is not null
             order by p.deletedAt desc
            """)
    List<Project> findDeletedRoots(UUID organizationId);

    @Query("""
            select p from Project p
             where p.id = :id
               and p.organization.id = :organizationId
               and p.deletedAt is not null
            """)
    Optional<Project> findDeletedByIdAndOrg(UUID id, UUID organizationId);

    @Query("""
            select p from Project p
             where p.deletedAt is not null
               and p.deletedAt < :cutoff
               and p.organization.deletedAt is null
            """)
    List<Project> findExpiredDeletedProjects(Instant cutoff);
}
