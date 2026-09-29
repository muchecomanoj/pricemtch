package com.priceintel.backend.dto.response;

import java.time.LocalDate;

import com.priceintel.backend.constants.AccountAccess;
import com.priceintel.backend.constants.SubscriptionStatus;
import com.priceintel.backend.constants.TenantStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The company's standing, sent with login and {@code /auth/me} so the screen
 * can show a banner and disable what will be refused, rather than letting the
 * user click and fail.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountStatusResponse {

    /** FULL, READ_ONLY or BLOCKED — the only field the screen needs to branch on. */
    private AccountAccess access;

    private TenantStatus tenantStatus;
    private SubscriptionStatus subscriptionStatus;
    private String plan;

    /** The last day the company has paid for (or its trial end, if it never paid). */
    private LocalDate paidThrough;

    /** The last day of full access: paid-through plus the grace days. */
    private LocalDate accessEndsOn;

    /** Grace days left, when paid-through has passed but access has not ended yet. */
    private Integer graceDaysLeft;

    /** A sentence for the banner; null when there is nothing to say. */
    private String message;
}
