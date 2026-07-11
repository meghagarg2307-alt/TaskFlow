package io.taskflow.dto.org;

import java.util.UUID;

/**
 * Result of redeeming an invitation — includes the joined org so the SPA can
 * {@code switch-org} without an extra round trip.
 */
public record AcceptInvitationResult(
        UUID organizationId,
        String organizationName,
        String organizationSlug,
        MemberResponse member
) {}
