package io.taskflow.repository;

import io.taskflow.domain.OrganizationMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembership, UUID> {

    /**
     * Resolve the user's role in a given org. Uses {@code uk_membership_user_org}
     * so it's a unique-index lookup (single row, no scan).
     */
    @Query("""
            select m from OrganizationMembership m
              join fetch m.organization o
             where m.user.id = :userId
               and m.organization.id = :organizationId
            """)
    Optional<OrganizationMembership> findByUserAndOrganization(UUID userId, UUID organizationId);

    @Query("""
            select m from OrganizationMembership m
              join fetch m.organization
             where m.user.id = :userId
             order by m.createdAt asc
            """)
    List<OrganizationMembership> findAllForUser(UUID userId);

    /** Members of an org, with user pre-fetched. Used by the org settings page. */
    @Query("""
            select m from OrganizationMembership m
              join fetch m.user
             where m.organization.id = :organizationId
             order by m.createdAt asc
            """)
    List<OrganizationMembership> findAllInOrganization(UUID organizationId);

    long countByOrganization_IdAndRole(UUID organizationId, io.taskflow.domain.enums.OrganizationRole role);

    boolean existsByUser_IdAndOrganization_Id(UUID userId, UUID organizationId);
}
