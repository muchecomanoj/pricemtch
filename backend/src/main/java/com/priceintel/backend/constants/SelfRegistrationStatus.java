package com.priceintel.backend.constants;

/**
 * Lifecycle of a self-service signup (from the landing page) BEFORE a real
 * tenant/user exists. The tenant is only created once payment completes.
 */
public enum SelfRegistrationStatus {
    PENDING_VERIFICATION,  // profile submitted, code emailed
    VERIFIED,              // code confirmed
    PENDING_PAYMENT,       // password set, awaiting payment
    COMPLETED,             // paid → tenant + user created
    EXPIRED
}
