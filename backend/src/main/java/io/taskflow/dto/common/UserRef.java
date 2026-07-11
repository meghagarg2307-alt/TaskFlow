package io.taskflow.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * Minimal user reference embedded in other DTOs (assignee, author, actor).
 * Kept separate from {@code UserSummary} so the auth response can evolve
 * independently of resource responses.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserRef(UUID id, String fullName, String avatarUrl) {}
