package com.priceintel.backend.constants;

/**
 * Actions recorded in the subscription history for a tenant.
 */
public enum SubscriptionAction {
    ASSIGNED,
    UPGRADED,
    DOWNGRADED,
    /** A downgrade was requested but deferred to the end of the current period. */
    DOWNGRADE_SCHEDULED,
    /** A pay link was issued for an upgrade/downgrade (awaiting payment). */
    PAYMENT_REQUESTED,
    /** A payment was confirmed (Stripe webhook / mock). */
    PAYMENT_RECEIVED,
    RENEWED,
    EXTENDED,
    CANCELLED,
    STATUS_CHANGED,
    /** The nightly sweep found the paid-through date, plus grace, behind us. */
    EXPIRED,
    /** The free trial ended and the company has paid for the period after it. */
    TRIAL_ENDED
}
