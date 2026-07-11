package io.taskflow.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {
    public NotFoundException(String resource, Object id) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND",
                "%s with id %s not found".formatted(resource, id));
    }
    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
