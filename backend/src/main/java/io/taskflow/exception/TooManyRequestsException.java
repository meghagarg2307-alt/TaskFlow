package io.taskflow.exception;

import org.springframework.http.HttpStatus;

import java.time.Duration;

/**
 * 429 — caller exceeded their quota. Carries an optional retry-after hint that the
 * exception handler can map to the {@code Retry-After} header.
 */
public class TooManyRequestsException extends ApiException {

    private final Duration retryAfter;

    public TooManyRequestsException(String code, String message, Duration retryAfter) {
        super(HttpStatus.TOO_MANY_REQUESTS, code, message);
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
