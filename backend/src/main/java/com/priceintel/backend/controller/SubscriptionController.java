package com.priceintel.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.priceintel.backend.dto.request.ChangeSubscriptionStatusRequest;
import com.priceintel.backend.dto.request.ExtendSubscriptionRequest;
import com.priceintel.backend.dto.request.PlanRequest;
import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.dto.response.PagedResponse;
import com.priceintel.backend.dto.response.PaymentResponse;
import com.priceintel.backend.dto.response.PlanChangeResponse;
import com.priceintel.backend.dto.response.PlanResponse;
import com.priceintel.backend.dto.response.SubscriptionHistoryResponse;
import com.priceintel.backend.dto.response.TenantResponse;
import com.priceintel.backend.service.SubscriptionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * SUPER_ADMIN subscription plan management and per-tenant subscription lifecycle.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "SUPER_ADMIN · Subscriptions", description = "Manage plans and tenant subscriptions")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    // ---- plans ----
    @GetMapping("/plans")
    @Operation(summary = "List subscription plans. Pass activeOnly=true for plan pickers (hides inactive plans).")
    public ResponseEntity<ApiResponse<List<PlanResponse>>> listPlans(
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly) {
        List<PlanResponse> plans = activeOnly
                ? subscriptionService.listActivePlans() : subscriptionService.listPlans();
        return ResponseEntity.ok(ApiResponse.success(plans, "Plans"));
    }

    @PostMapping("/plans")
    @Operation(summary = "Create a subscription plan")
    public ResponseEntity<ApiResponse<PlanResponse>> createPlan(@Valid @RequestBody PlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(subscriptionService.createPlan(request), "Plan created"));
    }

    @PutMapping("/plans/{id}")
    @Operation(summary = "Update a subscription plan")
    public ResponseEntity<ApiResponse<PlanResponse>> updatePlan(
            @PathVariable Long id, @RequestBody PlanRequest request) {
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.updatePlan(id, request), "Plan updated"));
    }

    // ---- per-tenant subscription ----
    @PostMapping("/clients/{tenantId}/subscription/assign")
    @Operation(summary = "Assign a plan directly — active immediately, no payment, no client email")
    public ResponseEntity<ApiResponse<TenantResponse>> assign(
            @PathVariable Long tenantId, @RequestParam String planCode,
            @RequestParam(required = false) com.priceintel.backend.constants.BillingCycle billingCycle) {
        return ResponseEntity.ok(ApiResponse.success(
                subscriptionService.assignPlan(tenantId, planCode, billingCycle), "Plan assigned"));
    }

    @PostMapping("/clients/{tenantId}/subscription/upgrade")
    @Operation(summary = "Upgrade a client's plan — emails the client a Stripe pay link; plan changes once paid")
    public ResponseEntity<ApiResponse<PlanChangeResponse>> upgrade(
            @PathVariable Long tenantId, @RequestParam String planCode,
            @RequestParam(required = false) com.priceintel.backend.constants.BillingCycle billingCycle) {
        PlanChangeResponse res = subscriptionService.upgrade(tenantId, planCode, billingCycle);
        return ResponseEntity.ok(ApiResponse.success(res, res.getMessage()));
    }

    @PostMapping("/clients/{tenantId}/subscription/downgrade")
    @Operation(summary = "Downgrade a client's plan — scheduled for the end of the current period (no payment now)")
    public ResponseEntity<ApiResponse<PlanChangeResponse>> downgrade(
            @PathVariable Long tenantId, @RequestParam String planCode,
            @RequestParam(required = false) com.priceintel.backend.constants.BillingCycle billingCycle) {
        PlanChangeResponse res = subscriptionService.downgrade(tenantId, planCode, billingCycle);
        return ResponseEntity.ok(ApiResponse.success(res, res.getMessage()));
    }

    @PostMapping("/clients/{tenantId}/subscription/renew")
    @Operation(summary = "Renew a client's subscription")
    public ResponseEntity<ApiResponse<TenantResponse>> renew(@PathVariable Long tenantId) {
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.renew(tenantId), "Subscription renewed"));
    }

    @PostMapping("/clients/{tenantId}/subscription/extend")
    @Operation(summary = "Extend a client's subscription validity")
    public ResponseEntity<ApiResponse<TenantResponse>> extend(
            @PathVariable Long tenantId, @Valid @RequestBody ExtendSubscriptionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                subscriptionService.extend(tenantId, request.getDays()), "Subscription extended"));
    }

    @PostMapping("/clients/{tenantId}/subscription/cancel")
    @Operation(summary = "Cancel a client's subscription")
    public ResponseEntity<ApiResponse<TenantResponse>> cancel(@PathVariable Long tenantId) {
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.cancel(tenantId), "Subscription cancelled"));
    }

    @PatchMapping("/clients/{tenantId}/subscription/status")
    @Operation(summary = "Change a client's subscription status")
    public ResponseEntity<ApiResponse<TenantResponse>> changeStatus(
            @PathVariable Long tenantId, @Valid @RequestBody ChangeSubscriptionStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                subscriptionService.changeStatus(tenantId, request.getStatus()), "Subscription status changed"));
    }

    @GetMapping("/clients/{tenantId}/subscription/history")
    @Operation(summary = "View a client's subscription history")
    public ResponseEntity<ApiResponse<PagedResponse<SubscriptionHistoryResponse>>> history(
            @PathVariable Long tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                subscriptionService.history(tenantId, page, size), "Subscription history"));
    }

    // ---- payment history ----
    @GetMapping("/payments")
    @Operation(summary = "All payments across all clients (super admin)")
    public ResponseEntity<ApiResponse<PagedResponse<PaymentResponse>>> allPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                subscriptionService.listPayments(page, size), "Payments"));
    }


    @GetMapping("/clients/{tenantId}/payments")
    @Operation(summary = "A single client's payment history")
    public ResponseEntity<ApiResponse<PagedResponse<PaymentResponse>>> clientPayments(
            @PathVariable Long tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                subscriptionService.listTenantPayments(tenantId, page, size), "Client payments"));
    }
}
