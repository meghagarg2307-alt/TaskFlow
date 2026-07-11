package io.taskflow.dto.org;

import jakarta.validation.constraints.NotBlank;

public record AcceptInvitationRequest(@NotBlank String token) {}
