package io.taskflow.exception;

import org.springframework.http.HttpStatus;

/**
 * Root of every controlled exception thrown by the application.
 *
 * <p>Carries an HTTP status and a stable machine-readable error code, separating
 * <em>what went wrong</em> (status/code) from <em>how we explain it</em> (message).
 * Clients should branch on {@link #getErrorCode()}, never on the human-readable
 * message — messages are translatable; codes are part of the API contract.</p>
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    protected ApiException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    protected ApiException(HttpStatus status, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
