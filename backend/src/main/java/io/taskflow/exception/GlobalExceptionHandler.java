package io.taskflow.exception;

import io.taskflow.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import jakarta.persistence.OptimisticLockException;

import java.time.Instant;
import java.util.List;

/**
 * Single funnel for translating exceptions into the {@link ApiError} envelope.
 *
 * <p>The handlers are deliberately ordered from most-specific to least-specific.
 * Logging level is also tuned: client errors (4xx) are logged at INFO/DEBUG to keep
 * production logs clean; only server errors (5xx) get WARN/ERROR.</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest req) {
        log.debug("API exception {} at {}: {}", ex.getErrorCode(), req.getRequestURI(), ex.getMessage());
        ResponseEntity<ApiError> response = build(ex.getStatus(), ex.getErrorCode(), ex.getMessage(), req, null);
        if (ex instanceof TooManyRequestsException too && too.getRetryAfter() != null) {
            return ResponseEntity.status(response.getStatusCode())
                    .header("Retry-After", String.valueOf(too.getRetryAfter().getSeconds()))
                    .body(response.getBody());
        }
        return response;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex,
                                                     HttpServletRequest req) {
        log.debug("Unreadable request body at {}: {}", req.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_BODY",
                "Request body is malformed or contains invalid values", req, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ApiError.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Request validation failed", req, errors);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "You do not have permission to perform this action", req, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuth(AuthenticationException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                "Authentication required", req, null);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ApiError> handleOptimisticLock(Exception ex, HttpServletRequest req) {
        log.info("Optimistic lock collision on {}", req.getRequestURI());
        return build(HttpStatus.CONFLICT, "STALE_RESOURCE",
                "The resource was modified by another user. Please refresh and retry.", req, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex,
                                                        HttpServletRequest req) {
        // Avoid leaking constraint names / SQL details to clients
        log.warn("Data integrity violation at {}: {}", req.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "DATA_INTEGRITY",
                "The request conflicts with the current state of the resource", req, null);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiError> handleNoHandler(NoHandlerFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "ROUTE_NOT_FOUND",
                "No handler for " + ex.getHttpMethod() + " " + ex.getRequestURL(), req, null);
    }

    /** Catch-all: bug or unexpected runtime failure → 500, but never leak stack to client. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception at {} {}", req.getMethod(), req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again later.", req, null);
    }

    private ApiError.FieldError toFieldError(FieldError fe) {
        return new ApiError.FieldError(fe.getField(), fe.getDefaultMessage());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message,
                                           HttpServletRequest req,
                                           List<ApiError.FieldError> fieldErrors) {
        ApiError body = ApiError.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .code(code)
                .message(message)
                .path(req.getRequestURI())
                .traceId(MDC.get("traceId"))
                .fieldErrors(fieldErrors)
                .build();
        return ResponseEntity.status(status).body(body);
    }
}
