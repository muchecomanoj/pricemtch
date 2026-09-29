package com.priceintel.backend.constants;

/**
 * What a payment was for.
 */
public enum PaymentType {
    /** First payment at onboarding / self-registration. */
    INITIAL,
    /** Paying to move to a higher plan (applied instantly once paid). */
    UPGRADE,
    /** Paying for a scheduled downgrade that took effect at period end. */
    DOWNGRADE,
    /** Renewing the current plan for another period. */
    RENEWAL
}
