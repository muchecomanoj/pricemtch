package com.priceintel.backend.service;

import java.util.List;

import com.priceintel.backend.dto.request.PlanRequest;
import com.priceintel.backend.dto.response.PagedResponse;
import com.priceintel.backend.dto.response.PaymentResponse;
import com.priceintel.backend.dto.response.PlanChangeResponse;
import com.priceintel.backend.dto.response.PlanResponse;
import com.priceintel.backend.dto.response.SubscriptionHistoryResponse;
import com.priceintel.backend.dto.response.TenantResponse;

/**
 * Subscription plan management and per-tenant subscription lifecycle (SUPER_ADMIN).
 */
public interface SubscriptionService {

    // ---- plan master data ----
    List<PlanResponse> listPlans();

    List<PlanResponse> listActivePlans();

    PlanResponse createPlan(PlanRequest request);

    PlanResponse updatePlan(Long id, PlanRequest request);

    // ---- per-tenant subscription ----
    /**
     * Assign a plan directly (super admin) — takes effect immediately, no payment
     * and no client email. {@code cycle} is optional; when null the tenant's
     * current billing cycle is kept.
     */
    TenantResponse assignPlan(Long tenantId, String planCode,
                              com.priceintel.backend.constants.BillingCycle cycle);

    /**
     * Upgrade: creates a Stripe pay link, emails it to the client; plan changes once paid.
     * {@code cycle} is optional — when null the tenant's current billing cycle is kept.
     */
    PlanChangeResponse upgrade(Long tenantId, String planCode,
                               com.priceintel.backend.constants.BillingCycle cycle);

    /**
     * Downgrade: scheduled for the end of the current period; no payment now.
     * {@code cycle} is optional — when null the tenant's current billing cycle is kept.
     */
    PlanChangeResponse downgrade(Long tenantId, String planCode,
                                 com.priceintel.backend.constants.BillingCycle cycle);

    TenantResponse renew(Long tenantId);

    TenantResponse extend(Long tenantId, int days);

    TenantResponse cancel(Long tenantId);

    TenantResponse changeStatus(Long tenantId, com.priceintel.backend.constants.SubscriptionStatus status);

    PagedResponse<SubscriptionHistoryResponse> history(Long tenantId, int page, int size);

    // ---- payments ----
    /** Applies a paid payment to its tenant (called by the Stripe webhook). */
    void applyPaidPayment(Long paymentId, String method);

    /** All payments across all tenants (super admin history). */
    PagedResponse<PaymentResponse> listPayments(int page, int size);

    /** One tenant's payments. */
    PagedResponse<PaymentResponse> listTenantPayments(Long tenantId, int page, int size);

    /** Applies any scheduled downgrades whose effective date has passed (daily job). */
    int runDueScheduledDowngrades();

    /** Reconciles pending upgrade/downgrade payments against Stripe (scheduled job). */
    int reconcilePendingPayments();

    /**
     * Brings stored subscription status in line with the dates: ends trials
     * that have been paid past, and expires companies whose paid-through date
     * plus grace has passed. Returns how many companies changed.
     */
    int runExpirySweep();

    /**
     * The customer renews their own current plan: a Stripe payment for one more
     * billing period, applied when it is paid. The period is added to the
     * current end date if that has not passed, so renewing early loses nothing.
     */
    PlanChangeResponse renewSelf(Long tenantId, com.priceintel.backend.constants.BillingCycle requestedCycle);
}
