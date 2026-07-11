package io.taskflow.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

/**
 * Uniform error envelope returned by {@code GlobalExceptionHandler}.
 *
 * <p>The shape is deliberately small and stable:</p>
 * <ul>
 *   <li>{@code code} — machine-readable identifier, never changes between releases</li>
 *   <li>{@code message} — human readable, may change/localize</li>
 *   <li>{@code fieldErrors} — populated only for validation failures</li>
 *   <li>{@code traceId} — propagated from MDC; lets users paste an ID for support</li>
 * </ul>
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String traceId,
        List<FieldError> fieldErrors
) {
    public record FieldError(String field, String message) {}
}
