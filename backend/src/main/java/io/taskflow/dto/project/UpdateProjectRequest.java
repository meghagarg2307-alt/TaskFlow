package io.taskflow.dto.project;

import jakarta.validation.constraints.Size;

/**
 * Patch-style: only non-null fields are applied. {@code description} sentinel for
 * "clear" is the empty string (we treat it as "unset description").
 */
public record UpdateProjectRequest(
        @Size(min = 2, max = 120) String name,
        @Size(max = 2000) String description
) {}
