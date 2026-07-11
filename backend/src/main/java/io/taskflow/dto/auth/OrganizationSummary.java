package io.taskflow.dto.auth;

import io.taskflow.domain.enums.OrganizationRole;

import java.util.UUID;

public record OrganizationSummary(
        UUID id,
        String name,
        String slug,
        OrganizationRole role
) {}
