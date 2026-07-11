package io.taskflow.dto.board;

import jakarta.validation.constraints.Size;

public record UpdateBoardRequest(
        @Size(min = 2, max = 120) String name,
        @Size(max = 2000) String description,
        /** Optimistic concurrency token. Required for any mutation. */
        Long expectedVersion
) {}
