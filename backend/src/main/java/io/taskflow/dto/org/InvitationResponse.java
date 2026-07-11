package io.taskflow.dto.org;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.taskflow.domain.enums.InvitationStatus;
import io.taskflow.domain.enums.OrganizationRole;

import java.time.Instant;
import java.util.UUID;

/**
 * Response for invitation list / create endpoints.
 *
 * <p>{@code inviteToken} is populated <b>only on the create response</b> — the
 * single moment the inviting admin can capture and share it. We never echo it back
 * on subsequent reads (the field is JSON-included only when non-null).</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InvitationResponse(
        UUID id,
        String email,
        OrganizationRole role,
        InvitationStatus status,
        Instant expiresAt,
        Instant createdAt,
        String inviteToken
) {}
