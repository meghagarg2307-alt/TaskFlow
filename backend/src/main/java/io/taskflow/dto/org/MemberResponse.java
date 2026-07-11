package io.taskflow.dto.org;

import io.taskflow.domain.enums.OrganizationRole;

import java.time.Instant;
import java.util.UUID;

public record MemberResponse(
        UUID userId,
        String email,
        String fullName,
        String avatarUrl,
        OrganizationRole role,
        Instant joinedAt
) {}
