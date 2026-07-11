package io.taskflow.repository;

import io.taskflow.domain.Invitation;
import io.taskflow.domain.enums.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    Optional<Invitation> findByTokenHash(String tokenHash);

    @Query("""
            select i from Invitation i
             where i.organization.id = :organizationId
               and i.status = :status
             order by i.createdAt desc
            """)
    List<Invitation> findByOrgAndStatus(UUID organizationId, InvitationStatus status);

    @Query("""
            select i from Invitation i
             where i.organization.id = :organizationId
               and lower(i.email) = lower(:email)
               and i.status = 'PENDING'
            """)
    Optional<Invitation> findPendingByEmail(UUID organizationId, String email);
}
