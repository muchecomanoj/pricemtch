package com.priceintel.backend.controller;

import java.util.List;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;

import com.priceintel.backend.constants.PaymentStatus;
import com.priceintel.backend.constants.PaymentType;
import com.priceintel.backend.dto.response.PaymentHistoryResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.priceintel.backend.dto.request.UpdateTenantRequest;
import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.dto.response.PagedResponse;
import com.priceintel.backend.dto.response.PaymentResponse;
import com.priceintel.backend.dto.response.PlanChangeResponse;
import com.priceintel.backend.dto.response.PlanResponse;
import com.priceintel.backend.dto.response.TenantResponse;
import com.priceintel.backend.dto.response.TenantStatsResponse;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.security.TenantContext;
import com.priceintel.backend.service.SubscriptionService;
import com.priceintel.backend.service.TenantService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Client (Tenant Administrator) self-service for their OWN organization only.
 * The tenant is always taken from the caller's context — never a path parameter —
 * so a client can never touch another tenant.
 */
@RestController
@RequestMapping("/api/v1/client")
@RequiredArgsConstructor
@Tag(name = "Client · My Company", description = "Tenant admin manages their own company profile and views subscription")
@PreAuthorize("hasRole('ADMIN')")
public class ClientController {

    private final TenantService tenantService;
    private final SubscriptionService subscriptionService;

    @GetMapping("/company")
    @Operation(summary = "View my company profile")
    public ResponseEntity<ApiResponse<TenantResponse>> company() {
        return ResponseEntity.ok(ApiResponse.success(tenantService.getTenant(currentTenant()), "Company profile"));
    }

    @PutMapping("/company")
    @Operation(summary = "Update my company profile / settings")
    public ResponseEntity<ApiResponse<TenantResponse>> updateCompany(
            @Valid @RequestBody UpdateTenantRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                tenantService.updateTenant(currentTenant(), request), "Company updated"));
    }

    @GetMapping("/subscription")
    @Operation(summary = "View my subscription details (read only)")
    public ResponseEntity<ApiResponse<TenantResponse>> subscription() {
        return ResponseEntity.ok(ApiResponse.success(
                tenantService.getTenant(currentTenant()), "Subscription details"));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "My organization dashboard / usage")
    public ResponseEntity<ApiResponse<TenantStatsResponse>> dashboard() {
        return ResponseEntity.ok(ApiResponse.success(tenantService.getStats(currentTenant()), "Dashboard"));
    }

    // ---- self-service plan management (own tenant only) ----

    @GetMapping("/plans")
    @Operation(summary = "Plans I can choose from (active plans)")
    public ResponseEntity<ApiResponse<List<PlanResponse>>> plans() {
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.listActivePlans(), "Available plans"));
    }

    @PostMapping("/subscription/upgrade")
    @Operation(summary = "Upgrade my plan — I receive a Stripe pay link; the plan changes once I pay")
    public ResponseEntity<ApiResponse<PlanChangeResponse>> upgrade(
            @RequestParam String planCode,
            @RequestParam(required = false) com.priceintel.backend.constants.BillingCycle billingCycle) {
        PlanChangeResponse res = subscriptionService.upgrade(currentTenant(), planCode, billingCycle);
        return ResponseEntity.ok(ApiResponse.success(res, res.getMessage()));
    }

    @PostMapping("/subscription/renew")
    @Operation(summary = "Renew my current plan — pay on Stripe for one more period; "
            + "added to my end date if it has not passed yet")
    public ResponseEntity<ApiResponse<PlanChangeResponse>> renew(
            @RequestParam(required = false) com.priceintel.backend.constants.BillingCycle billingCycle) {
        PlanChangeResponse res = subscriptionService.renewSelf(currentTenant(), billingCycle);
        return ResponseEntity.ok(ApiResponse.success(res, res.getMessage()));
    }

    @PostMapping("/subscription/downgrade")
    @Operation(summary = "Downgrade my plan — takes effect at the end of my current period (no payment now)")
    public ResponseEntity<ApiResponse<PlanChangeResponse>> downgrade(
            @RequestParam String planCode,
            @RequestParam(required = false) com.priceintel.backend.constants.BillingCycle billingCycle) {
        PlanChangeResponse res = subscriptionService.downgrade(currentTenant(), planCode, billingCycle);
        return ResponseEntity.ok(ApiResponse.success(res, res.getMessage()));
    }

    @GetMapping("/payments")
    @Operation(summary = "My payment history, filtered",
            description = "Returns the rows, what they add up to, and the months that hold a "
                    + "payment.\n\n"
                    + "`month` is `YYYY-MM` and is the whole of that month — what the month picker "
                    + "sends. `from` and `to` are `YYYY-MM-DD` and are inclusive of both days; "
                    + "given either, `month` is ignored. `status` is PENDING, PAID, FAILED or "
                    + "CANCELLED; `type` is INITIAL, UPGRADE, DOWNGRADE or RENEWAL. Everything is "
                    + "optional and any omitted filter is ignored.\n\n"
                    + "Filtered on the date each row shows, so October means the rows dated "
                    + "October. A payment started on 31 October and paid on 1 November belongs to "
                    + "October, which is where someone scrolling the list would look for it.\n\n"
                    + "`months` is not narrowed by the other filters — a month must not vanish "
                    + "from the picker because of a status the user is about to change.\n\n"
                    + "`summary.paidAmount` is null with `mixedCurrencies: true` when the filtered "
                    + "payments span more than one currency; show the counts and drop the total, "
                    + "because no single number would be true.")
    public ResponseEntity<ApiResponse<PaymentHistoryResponse>> payments(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) PaymentType type,
            @RequestParam(required = false) String month,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                subscriptionService.paymentHistory(
                        currentTenant(), status, type, month, from, to, page, size),
                "My payments"));
    }

    private Long currentTenant() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BadRequestException("No tenant context");
        }
        return tenantId;
    }
}
