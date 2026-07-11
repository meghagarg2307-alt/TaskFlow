package io.taskflow.dto.org;

import io.taskflow.domain.enums.OrganizationRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InviteMemberRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotNull OrganizationRole role
) {}
