package io.taskflow.dto.board;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateColumnRequest(
        @NotBlank @Size(min = 1, max = 80) String name,
        @Min(1) Integer wipLimit
) {}
