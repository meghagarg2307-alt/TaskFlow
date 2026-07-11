package io.taskflow.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Register a new user. We also create a personal organization for them in the same
 * transaction (Slack/Notion-style onboarding), so they land on a usable workspace
 * immediately. Joining other orgs happens via the invitation flow.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(min = 2, max = 120) String fullName,
        @NotBlank @Size(min = 2, max = 120) String organizationName,
        @NotBlank @Size(min = 2, max = 64)
            @Pattern(regexp = "^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$",
                    message = "slug must be lowercase letters, digits, and dashes")
            String organizationSlug
) {}
