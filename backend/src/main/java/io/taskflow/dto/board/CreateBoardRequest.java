package io.taskflow.dto.board;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBoardRequest(
        @NotBlank @Size(min = 2, max = 120) String name,
        @Size(max = 2000) String description
) {}
