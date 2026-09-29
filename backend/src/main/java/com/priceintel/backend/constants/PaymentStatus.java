package com.priceintel.backend.constants;

/**
 * Lifecycle of a payment record.
 */
public enum PaymentStatus {
    /** Checkout created / pay link sent; awaiting the client to pay. */
    PENDING,
    /** Confirmed paid (Stripe webhook, or mock/manual confirmation). */
    PAID,
    /** Payment attempt failed. */
    FAILED,
    /** Superseded or abandoned (e.g. a newer plan change replaced it). */
    CANCELLED
}
