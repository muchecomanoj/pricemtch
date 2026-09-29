package com.priceintel.backend.constants;

/**
 * How often a subscription is billed. Drives the period length and price.
 */
public enum BillingCycle {
    MONTHLY(30),
    YEARLY(365);

    private final int days;

    BillingCycle(int days) {
        this.days = days;
    }

    public int getDays() {
        return days;
    }
}
