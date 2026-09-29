package com.priceintel.backend.exception;

/**
 * Thrown when a refresh token is invalid, expired, or revoked. Mapped to
 * HTTP 403 Forbidden.
 */
public class TokenRefreshException extends RuntimeException {

    public TokenRefreshException(String message) {
        super(message);
    }
}
