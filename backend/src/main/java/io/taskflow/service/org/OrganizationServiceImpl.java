package io.taskflow.service.org;

import io.taskflow.common.tenant.TenantContext;
import io.taskflow.domain.Invitation;
import io.taskflow.domain.Organization;
import io.taskflow.domain.OrganizationMembership;
import io.taskflow.domain.User;
import io.taskflow.domain.enums.ActivityType;
import io.taskflow.domain.enums.InvitationStatus;
import io.taskflow.domain.enums.OrganizationRole;
import io.taskflow.dto.org.AcceptInvitationRequest;
import io.taskflow.dto.org.AcceptInvitationResult;
import io.taskflow.dto.org.InvitationResponse;
import io.taskflow.dto.org.InviteMemberRequest;
import io.taskflow.dto.org.MemberResponse;
import io.taskflow.dto.org.OrganizationResponse;
import io.taskflow.dto.org.UpdateMemberRoleRequest;
import io.taskflow.dto.org.UpdateOrganizationRequest;
import io.taskflow.exception.BadRequestException;
import io.taskflow.exception.ConflictException;
import io.taskflow.exception.ForbiddenException;
import io.taskflow.exception.NotFoundException;
import io.taskflow.repository.InvitationRepository;
import io.taskflow.repository.OrganizationMembershipRepository;
import io.taskflow.repository.OrganizationRepository;
import io.taskflow.repository.UserRepository;
import io.taskflow.service.trash.SoftDeleteCascadeService;
import io.taskflow.security.RefreshTokenService;
import io.taskflow.service.activity.ActivityPublisher;
import io.taskflow.service.cache.MembershipCache;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizationServiceImpl implements OrganizationService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;
    private static final Duration INVITE_TTL = Duration.ofDays(7);

    private final OrganizationRepository organizations;
    private final OrganizationMembershipRepository memberships;
    private final UserRepository users;
    private final InvitationRepository invitations;
    private final ActivityPublisher activity;
    private final RefreshTokenService refreshTokens;
    private final MembershipCache membershipCache;
    private final EntityManager entityManager;
    private final SoftDeleteCascadeService softDeleteCascade;

    // ----------------------------------------------------------- organization

    @Override
    @Transactional(readOnly = true)
    public OrganizationResponse getCurrent() {
        return toOrganizationResponse(loadActiveOrg());
    }

    @Override
    @Transactional
    public OrganizationResponse updateCurrent(UpdateOrganizationRequest request) {
        if (!TenantContext.get().isAdmin()) {
            throw new ForbiddenException("ADMIN_REQUIRED",
                    "Only administrators can update workspace settings");
        }
        Organization org = loadActiveOrg();
        if (request.name() != null) org.setName(request.name());
        if (request.description() != null) org.setDescription(request.description());
        activity.publish(ActivityType.ORGANIZATION_UPDATED, null, null, null,
                Map.of("name", org.getName()));
        return toOrganizationResponse(org);
    }

    @Override
    @Transactional
    public void softDeleteCurrent() {
        if (!TenantContext.get().isAdmin()) {
            throw new ForbiddenException("ADMIN_REQUIRED",
                    "Only administrators can delete the workspace");
        }
        UUID orgId = TenantContext.requireOrganizationId();
        Organization org = loadActiveOrg();
        UUID deletedBy = TenantContext.requireUserId();
        Instant when = Instant.now();
        softDeleteCascade.softDeleteOrganization(orgId, deletedBy, when);
        activity.publish(ActivityType.ORGANIZATION_DELETED, null, null, null,
                Map.of("organizationId", org.getId(), "name", org.getName()));
    }

    private Organization loadActiveOrg() {
        UUID orgId = TenantContext.requireOrganizationId();
        Organization org = organizations.findById(orgId)
                .orElseThrow(() -> new NotFoundException("Organization", orgId));
        if (org.isDeleted()) {
            throw new NotFoundException("Organization", orgId);
        }
        return org;
    }

    private static OrganizationResponse toOrganizationResponse(Organization org) {
        return new OrganizationResponse(
                org.getId(), org.getName(), org.getSlug(), org.getDescription(),
                org.getCreatedAt(), org.getUpdatedAt());
    }

    // ----------------------------------------------------------------- members

    @Override
    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers() {
        return memberships.findAllInOrganization(TenantContext.requireOrganizationId()).stream()
                .map(OrganizationServiceImpl::toMemberResponse)
                .toList();
    }

    @Override
    @Transactional
    public MemberResponse updateMemberRole(UUID userId, UpdateMemberRoleRequest request) {
        if (!TenantContext.get().isAdmin()) {
            throw new ForbiddenException("Only admins may change member roles");
        }
        UUID orgId = TenantContext.requireOrganizationId();
        OrganizationMembership membership = memberships.findByUserAndOrganization(userId, orgId)
                .orElseThrow(() -> new NotFoundException("Membership for user", userId));

        // Self-demote protection: an admin can't demote themselves if they are the last admin.
        if (membership.getUser().getId().equals(TenantContext.requireUserId())
                && request.role() != OrganizationRole.ADMIN
                && countAdmins(orgId) <= 1) {
            throw new ConflictException("LAST_ADMIN",
                    "Cannot remove the last admin of this organization");
        }

        OrganizationRole previous = membership.getRole();
        membership.setRole(request.role());

        membershipCache.evict(userId);

        activity.publish(ActivityType.MEMBER_ROLE_CHANGED, null, null, null,
                Map.of("userId", userId, "previous", previous.name(), "next", request.role().name()));

        return toMemberResponse(membership);
    }

    @Override
    @Transactional
    public void removeMember(UUID userId) {
        if (!TenantContext.get().isAdmin()) {
            throw new ForbiddenException("Only admins may remove members");
        }
        UUID orgId = TenantContext.requireOrganizationId();
        OrganizationMembership membership = memberships.findByUserAndOrganization(userId, orgId)
                .orElseThrow(() -> new NotFoundException("Membership for user", userId));

        if (membership.getRole() == OrganizationRole.ADMIN && countAdmins(orgId) <= 1) {
            throw new ConflictException("LAST_ADMIN",
                    "Cannot remove the last admin of this organization");
        }

        memberships.delete(membership);
        // Belt-and-braces: kill any active refresh tokens for the removed user so they
        // can't continue using this org via a still-valid access token.
        refreshTokens.revokeAllForUser(userId);
        membershipCache.evict(userId);

        activity.publish(ActivityType.MEMBER_REMOVED, null, null, null, Map.of("userId", userId));
    }

    // ------------------------------------------------------------ invitations

    @Override
    @Transactional
    public InvitationResponse invite(InviteMemberRequest request) {
        if (!TenantContext.get().isAtLeastManager()) {
            throw new ForbiddenException("Only managers or admins may invite members");
        }
        UUID orgId = TenantContext.requireOrganizationId();

        if (invitations.findPendingByEmail(orgId, request.email()).isPresent()) {
            throw new ConflictException("ALREADY_INVITED",
                    "An invitation for this email is already pending");
        }

        // If the user already exists AND already a member, refuse to send a new invite.
        users.findByEmailIgnoreCase(request.email()).ifPresent(u -> {
            if (memberships.existsByUser_IdAndOrganization_Id(u.getId(), orgId)) {
                throw new ConflictException("ALREADY_MEMBER",
                        "This user is already a member of the organization");
            }
        });

        String rawToken = generateOpaqueToken();
        String tokenHash = sha256Hex(rawToken);

        Invitation invitation = Invitation.builder()
                .organization(entityManager.getReference(Organization.class, orgId))
                .invitedBy(entityManager.getReference(User.class, TenantContext.requireUserId()))
                .email(request.email().toLowerCase())
                .role(request.role())
                .tokenHash(tokenHash)
                .status(InvitationStatus.PENDING)
                .expiresAt(Instant.now().plus(INVITE_TTL))
                .build();
        invitation = invitations.save(invitation);

        activity.publish(ActivityType.MEMBER_INVITED, null, null, null,
                Map.of("email", request.email(), "role", request.role().name()));

        // Return the raw token ONCE so the inviter can share it. Subsequent reads omit it.
        return new InvitationResponse(
                invitation.getId(), invitation.getEmail(), invitation.getRole(),
                invitation.getStatus(), invitation.getExpiresAt(),
                invitation.getCreatedAt(), rawToken);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvitationResponse> listPendingInvitations() {
        if (!TenantContext.get().isAtLeastManager()) {
            throw new ForbiddenException("Only managers or admins may view invitations");
        }
        UUID orgId = TenantContext.requireOrganizationId();
        return invitations.findByOrgAndStatus(orgId, InvitationStatus.PENDING).stream()
                .map(i -> new InvitationResponse(i.getId(), i.getEmail(), i.getRole(),
                        i.getStatus(), i.getExpiresAt(), i.getCreatedAt(), null))
                .toList();
    }

    @Override
    @Transactional
    public void revokeInvitation(UUID invitationId) {
        if (!TenantContext.get().isAtLeastManager()) {
            throw new ForbiddenException("Only managers or admins may revoke invitations");
        }
        UUID orgId = TenantContext.requireOrganizationId();
        Invitation invitation = invitations.findById(invitationId)
                .filter(i -> i.getOrganization().getId().equals(orgId))
                .orElseThrow(() -> new NotFoundException("Invitation", invitationId));
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new ConflictException("INVITATION_NOT_PENDING",
                    "Only pending invitations can be revoked");
        }
        invitation.setStatus(InvitationStatus.REVOKED);
    }

    // --------------------------------------------------------------- accept

    @Override
    @Transactional
    public AcceptInvitationResult acceptInvitation(AcceptInvitationRequest request) {
        UUID userId = TenantContext.requireUserId();
        User user = users.findById(userId)
                .orElseThrow(() -> new NotFoundException("User", userId));

        Invitation invitation = invitations.findByTokenHash(sha256Hex(request.token()))
                .orElseThrow(() -> new BadRequestException("INVALID_TOKEN", "Invitation token is invalid"));

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new ConflictException("INVITATION_NOT_PENDING", "Invitation is no longer valid");
        }
        if (invitation.getExpiresAt().isBefore(Instant.now())) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            throw new ConflictException("INVITATION_EXPIRED", "Invitation has expired");
        }
        if (!invitation.getEmail().equalsIgnoreCase(user.getEmail())) {
            // Token leakage: an invite for foo@x.com cannot be redeemed by bar@y.com.
            throw new ForbiddenException("EMAIL_MISMATCH",
                    "This invitation was issued to a different email address");
        }

        UUID orgId = invitation.getOrganization().getId();
        if (memberships.existsByUser_IdAndOrganization_Id(userId, orgId)) {
            throw new ConflictException("ALREADY_MEMBER",
                    "You are already a member of this organization");
        }

        OrganizationMembership membership = memberships.save(OrganizationMembership.builder()
                .user(user)
                .organization(invitation.getOrganization())
                .role(invitation.getRole())
                .build());

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(Instant.now());

        membershipCache.evict(userId);

        // Activity is recorded in the target org context (the inviter's org), not the
        // current TenantContext (which is the new joiner's currently active org).
        // We use a direct insert here to avoid context confusion.
        // (For brevity in v1: we skip the activity record on accept; the inviter sees
        // the pending->accepted state via /invitations listing.)

        Organization org = invitation.getOrganization();
        return new AcceptInvitationResult(
                org.getId(),
                org.getName(),
                org.getSlug(),
                toMemberResponse(membership));
    }

    // ----------------------------------------------------------- helpers

    private long countAdmins(UUID organizationId) {
        return memberships.countByOrganization_IdAndRole(organizationId, OrganizationRole.ADMIN);
    }

    private static MemberResponse toMemberResponse(OrganizationMembership m) {
        User u = m.getUser();
        return new MemberResponse(
                u.getId(), u.getEmail(), u.getFullName(), u.getAvatarUrl(),
                m.getRole(), m.getCreatedAt());
    }

    private static String generateOpaqueToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String value) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
