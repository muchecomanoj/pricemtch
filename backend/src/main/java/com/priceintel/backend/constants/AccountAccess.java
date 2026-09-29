package com.priceintel.backend.constants;

/**
 * What a company's users may do right now, derived from its status and dates.
 *
 * <p>One value decides login, every request, and the background jobs, so the
 * three can never disagree about whether a company is paid up.</p>
 */
public enum AccountAccess {

    /** Paid up or in trial: everything. */
    FULL,

    /**
     * Paid-through date passed (plus grace). They can sign in, see their data,
     * export it and pay to upgrade — but nothing that spends marketplace or AI
     * quota, and their monitors and alerts are paused.
     *
     * <p>Not a lockout on purpose: payment is one-off, so a customer who cannot
     * sign in cannot pay, and would stay expired for ever.</p>
     */
    READ_ONLY,

    /** Suspended, cancelled, removed or never activated: no access at all. */
    BLOCKED
}
