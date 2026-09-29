package com.priceintel.backend.exception;

/**
 * Thrown when a marketplace API call fails (not configured, timeout, rate-limited
 * after retries, or an upstream error). Mapped to HTTP 502 by the global handler.
 */
public class MarketplaceApiException extends RuntimeException {

    public MarketplaceApiException(String message) {
        super(message);
    }

    public MarketplaceApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
