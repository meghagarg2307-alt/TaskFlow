package io.taskflow.controller;

import io.taskflow.dto.org.AcceptInvitationRequest;
import io.taskflow.dto.org.AcceptInvitationResult;
import io.taskflow.dto.org.InvitationResponse;
import io.taskflow.dto.org.InviteMemberRequest;
import io.taskflow.dto.org.MemberResponse;
import io.taskflow.dto.org.OrganizationResponse;
import io.taskflow.dto.org.UpdateMemberRoleRequest;
import io.taskflow.dto.org.UpdateOrganizationRequest;
import io.taskflow.service.org.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints scoped to the <em>current</em> organization (from the JWT). Avoids
 * embedding {@code organizationId} in the URL — the token decides which tenant the
 * request applies to. Cross-tenant ops require {@code POST /auth/switch-org} first.
 */
@RestController
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    @GetMapping("/organization")
    @PreAuthorize("hasRole('MEMBER')")
    public OrganizationResponse getCurrent() {
        return organizationService.getCurrent();
    }

    @PatchMapping("/organization")
    @PreAuthorize("hasRole('ADMIN')")
    public OrganizationResponse updateCurrent(@Valid @RequestBody UpdateOrganizationRequest req) {
        return organizationService.updateCurrent(req);
    }

    @DeleteMapping("/organization")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> softDeleteCurrent() {
        organizationService.softDeleteCurrent();
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- members

    @GetMapping("/organization/members")
    @PreAuthorize("hasRole('MEMBER')")
    public List<MemberResponse> listMembers() {
        return organizationService.listMembers();
    }

    @PatchMapping("/organization/members/{userId}")
    public MemberResponse updateRole(@PathVariable UUID userId,
                                     @Valid @RequestBody UpdateMemberRoleRequest req) {
        return organizationService.updateMemberRole(userId, req);
    }

    @DeleteMapping("/organization/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable UUID userId) {
        organizationService.removeMember(userId);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------ invitations

    @PostMapping("/organization/invitations")
    public InvitationResponse invite(@Valid @RequestBody InviteMemberRequest req) {
        return organizationService.invite(req);
    }

    @GetMapping("/organization/invitations")
    public List<InvitationResponse> listInvitations() {
        return organizationService.listPendingInvitations();
    }

    @DeleteMapping("/organization/invitations/{invitationId}")
    public ResponseEntity<Void> revoke(@PathVariable UUID invitationId) {
        organizationService.revokeInvitation(invitationId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Accept an invitation. The acting user is the JWT subject; the org is encoded
     * in the token. Requires an authenticated session (the SPA flow is: register/login
     * → accept invitation), which is why this lives <em>outside</em> {@code /auth}.
     */
    @PostMapping("/invitations/accept")
    public AcceptInvitationResult accept(@Valid @RequestBody AcceptInvitationRequest req) {
        return organizationService.acceptInvitation(req);
    }
}
