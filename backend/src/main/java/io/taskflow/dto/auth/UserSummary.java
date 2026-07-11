package io.taskflow.dto.auth;

import java.util.UUID;

public record UserSummary(
        UUID id,
        String email,
        String fullName,
        String avatarUrl
) {}
