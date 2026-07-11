package io.taskflow.dto.org;

import io.taskflow.domain.enums.OrganizationRole;
import jakarta.validation.constraints.NotNull;

public record UpdateMemberRoleRequest(@NotNull OrganizationRole role) {}
