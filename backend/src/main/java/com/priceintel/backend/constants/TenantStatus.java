package com.priceintel.backend.constants;

/**
 * Lifecycle status of a tenant (client organization). DELETED is a soft delete.
 */
public enum TenantStatus {
    /** Created by the platform, waiting for the client to activate via email. */
    PENDING_ACTIVATION,
    /** Verified + password set, but subscription payment not completed. */
    PENDING_PAYMENT,
    ACTIVE,
    INACTIVE,
    SUSPENDED,
    DELETED
}
