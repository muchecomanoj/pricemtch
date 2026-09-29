package com.priceintel.backend.exception;

/**
 * Thrown when creating a resource that already exists (e.g. duplicate email).
 * Mapped to HTTP 409 Conflict.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
