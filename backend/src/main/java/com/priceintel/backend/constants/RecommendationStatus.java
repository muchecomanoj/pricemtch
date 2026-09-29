package com.priceintel.backend.constants;

/** Lifecycle of a price recommendation (FR-REC-002). */
public enum RecommendationStatus {
    DRAFT,

    /**
     * Proposed and waiting for someone to sign it off (FR-REC-002).
     *
     * <p>Separates proposing from approving: a pricing manager can put a price
     * forward without being the person who authorises it. Optional — a small
     * team can approve a draft directly — but where it is used, this is the
     * queue an approver works from.</p>
     */
    PENDING_APPROVAL,

    APPROVED,
    PUBLISHED,
    REJECTED,
    EXPIRED
}
