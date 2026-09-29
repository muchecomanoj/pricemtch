package com.priceintel.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of an upgrade/downgrade request. For an upgrade a Stripe pay link is
 * created and emailed to the client (and returned here so the UI can show/copy
 * it); the plan only changes once paid. For a downgrade the change is scheduled
 * for the end of the current period — no payment now.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanChangeResponse {
    private TenantResponse tenant;
    /** "UPGRADE_PENDING_PAYMENT", "DOWNGRADE_SCHEDULED", or "APPLIED". */
    private String outcome;
    /** Stripe checkout URL for an upgrade (null when not applicable). */
    private String checkoutUrl;
    /** True when a pay link was emailed to the client. */
    private boolean payLinkSent;
    private String message;
}
