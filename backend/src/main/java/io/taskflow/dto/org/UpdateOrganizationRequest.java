package io.taskflow.dto.org;

import jakarta.validation.constraints.Size;

public record UpdateOrganizationRequest(
        @Size(min = 1, max = 120) String name,
        @Size(max = 1000) String description
) {}
