package com.priceintel.backend.constants;

/**
 * Lifecycle status of a user. DELETED is a soft delete.
 */
public enum UserStatus {
    /** Invited but not yet activated — no password set, cannot log in. */
    PENDING,
    ACTIVE,
    INACTIVE,
    DELETED
}
