package com.priceintel.backend.exception;

/**
 * Thrown when an AI provider call fails or is not configured. Mapped to HTTP 502.
 */
public class AiException extends RuntimeException {

    /**
     * Why the call failed, as a value rather than prose.
     *
     * <p>Callers need the difference: a rate limit clears on its own and the
     * user should try again, while "the model found nothing" is a real answer.
     * Reporting both as an empty result tells someone their product does not
     * exist when in fact nobody looked.</p>
     */
    public enum Reason {
        /** Provider quota — transient, worth retrying shortly. */
        RATE_LIMITED,
        /** The request or its accumulated context exceeded the provider's limit. */
        PAYLOAD_TOO_LARGE,
        /** Credentials rejected. */
        AUTHENTICATION,
        /** Anything else. */
        PROVIDER_ERROR,
        /** No provider configured at all. */
        NOT_CONFIGURED
    }

    private final Reason reason;

    public AiException(String message) {
        this(message, Reason.PROVIDER_ERROR, null);
    }

    public AiException(String message, Throwable cause) {
        this(message, Reason.PROVIDER_ERROR, cause);
    }

    public AiException(String message, Reason reason, Throwable cause) {
        super(message, cause);
        this.reason = reason == null ? Reason.PROVIDER_ERROR : reason;
    }

    public Reason getReason() {
        return reason;
    }

    /** Whether trying again in a moment could plausibly succeed. */
    public boolean isTransient() {
        return reason == Reason.RATE_LIMITED;
    }

    public static Reason reasonForStatus(int status) {
        return switch (status) {
            case 429 -> Reason.RATE_LIMITED;
            case 413 -> Reason.PAYLOAD_TOO_LARGE;
            case 401, 403 -> Reason.AUTHENTICATION;
            default -> Reason.PROVIDER_ERROR;
        };
    }
}
