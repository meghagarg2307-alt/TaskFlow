package io.taskflow.dto.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank @Size(min = 2, max = 120) String name,
        @NotBlank @Size(min = 2, max = 10)
            @Pattern(regexp = "^[A-Z][A-Z0-9]*$",
                    message = "key must be uppercase letters and digits, starting with a letter")
            String key,
        @Size(max = 2000) String description
) {}
