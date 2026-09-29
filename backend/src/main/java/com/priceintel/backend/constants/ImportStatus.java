package com.priceintel.backend.constants;

/**
 * Lifecycle of a bulk import job.
 */
public enum ImportStatus {
    PENDING,                 // accepted, queued for processing
    PROCESSING,              // rows being imported
    COMPLETED,               // all rows imported successfully
    COMPLETED_WITH_ERRORS,   // finished, but some rows failed / were skipped
    FAILED                   // fatal error (e.g. unreadable file)
}
