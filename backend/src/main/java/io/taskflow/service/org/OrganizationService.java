package io.taskflow.service.org;

import io.taskflow.dto.org.AcceptInvitationRequest;
import io.taskflow.dto.org.AcceptInvitationResult;
import io.taskflow.dto.org.InvitationResponse;
import io.taskflow.dto.org.InviteMemberRequest;
import io.taskflow.dto.org.MemberResponse;
import io.taskflow.dto.org.OrganizationResponse;
import io.taskflow.dto.org.UpdateMemberRoleRequest;
import io.taskflow.dto.org.UpdateOrganizationRequest;

import java.util.List;
import java.util.UUID;

public interface OrganizationService {

    OrganizationResponse getCurrent();
    OrganizationResponse updateCurrent(UpdateOrganizationRequest request);
    void softDeleteCurrent();

    List<MemberResponse> listMembers();
    MemberResponse updateMemberRole(UUID userId, UpdateMemberRoleRequest request);
    void removeMember(UUID userId);

    InvitationResponse invite(InviteMemberRequest request);
    List<InvitationResponse> listPendingInvitations();
    void revokeInvitation(UUID invitationId);

    /** Open endpoint — caller must be authenticated, but the org is determined by the token. */
    AcceptInvitationResult acceptInvitation(AcceptInvitationRequest request);
}
