package com.priceintel.backend.constants;

/**
 * How much a number can be trusted.
 *
 * <p>The platform mixes figures of very different provenance on one screen: a
 * price read from Amazon, a margin worked out from it, an estimate inferred
 * from thin signals, and a blank where nothing is known. Presented identically
 * they invite the same confidence, which is how someone ends up repricing
 * against a guess. Every number therefore carries one of these.</p>
 *
 * <p>Per FR-REPORT-001 this travels with the value into exports too — a CSV
 * opened in a spreadsheet is exactly where provenance is otherwise lost.</p>
 */
public enum ValueStatus {

    /** Read directly from a marketplace or an authorised account feed. */
    ACTUAL,

    /** Derived by arithmetic from actual values — a margin, a landed price. */
    CALCULATED,

    /** Inferred from incomplete signals, or computed from non-specific inputs. */
    ESTIMATED,

    /** Real, but observed longer ago than the freshness threshold. */
    STALE,

    /** Not known. Distinct from zero, which is a value. */
    UNAVAILABLE
}
