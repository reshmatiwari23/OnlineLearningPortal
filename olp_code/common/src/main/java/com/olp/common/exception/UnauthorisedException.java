package com.olp.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when authentication fails — wrong password, invalid token etc.
 * Maps to HTTP 401 Unauthorized.
 *
 * Example usage:
 *   throw new UnauthorisedException("Invalid email or password");
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class UnauthorisedException extends RuntimeException {

    public UnauthorisedException(String message) {
        super(message);
    }
}
