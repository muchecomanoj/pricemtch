package com.priceintel.backend.constants;

/**
 * Outcome of a single stage in the search waterfall (for the search trace).
 */
public enum SearchStepStatus {
    MATCHED,           // this stage found results
    NO_MATCH,          // executed, found nothing
    SKIPPED,           // not executed because an earlier stage already matched
    NOT_IMPLEMENTED    // placeholder stage (e.g. image search)
}
