package io.taskflow.dto.board;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Patch a column. {@code beforeColumnId}/{@code afterColumnId} reorder the column
 * relative to its siblings — the server computes the new position. Sending both as
 * null leaves the position untouched.
 */
public record UpdateColumnRequest(
        @Size(min = 1, max = 80) String name,
        @Min(0) Integer wipLimit,
        UUID beforeColumnId,
        UUID afterColumnId
) {}
