package com.priceintel.backend.exception;

/**
 * Thrown for invalid business input. Mapped to HTTP 400.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
