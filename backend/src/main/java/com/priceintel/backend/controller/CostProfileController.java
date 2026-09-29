package com.priceintel.backend.controller;

import java.math.BigDecimal;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.priceintel.backend.dto.request.CostProfileRequest;
import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.dto.response.CostProfileResponse;
import com.priceintel.backend.dto.response.ProfitabilityResponse;
import com.priceintel.backend.service.impl.CostProfileService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Product cost profile (FR-COST-001) and profitability (FR-PROFIT-001).
 */
@RestController
@RequestMapping("/api/v1/products/{id}")
@RequiredArgsConstructor
@Tag(name = "Product Cost & Profitability", description = "Cost inputs and deterministic profit/margin/ROI/break-even")
public class CostProfileController {

    // Finance maintains COGS/costs per FRD §3, alongside ADMIN/MANAGER.
    private static final String WRITE_ROLES = "hasAnyRole('ADMIN', 'MANAGER', 'FINANCE', 'SUPER_ADMIN')";

    private final CostProfileService service;
    private final com.priceintel.backend.service.impl.ProfitabilitySnapshotService snapshotService;

    @GetMapping("/cost-profile")
    @Operation(summary = "The costs in force today, or on a given date",
            description = "Pass `asOf=YYYY-MM-DD` to see the costs that applied then. Costs are "
                    + "kept as a dated series, so a past margin is explained by the costs that "
                    + "were true at the time rather than by today's.")
    public ResponseEntity<ApiResponse<CostProfileResponse>> getCostProfile(
            @PathVariable Long id,
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate asOf) {
        return ResponseEntity.ok(ApiResponse.success(
                service.getCostProfile(id, asOf == null ? java.time.LocalDate.now() : asOf),
                "Cost profile"));
    }

    @GetMapping("/cost-profile/history")
    @Operation(summary = "Every version of this product's costs, newest first",
            description = "One entry per effective date. Use it to show when a cost changed and "
                    + "what it was before — the question a margin that moved always raises.")
    public ResponseEntity<ApiResponse<java.util.List<CostProfileResponse>>> costHistory(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.costHistory(id), "Cost history"));
    }

    @PutMapping("/cost-profile")
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Record costs effective from a date",
            description = "`validFrom` is the date these costs take effect; omitting it means "
                    + "today. Saving against a date that already has a profile corrects that "
                    + "version — a new date adds one and leaves the earlier costs intact, so "
                    + "correcting a typo and recording a supplier increase stay distinguishable.\n\n"
                    + "A future `validFrom` is allowed and does not affect today's figures.")
    public ResponseEntity<ApiResponse<CostProfileResponse>> saveCostProfile(
            @PathVariable Long id, @Valid @RequestBody CostProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                service.saveCostProfile(id, request), "Cost profile saved"));
    }

    @GetMapping("/profitability")
    @Operation(summary = "Profit/margin/ROI/break-even. Defaults to the product's ourPrice; pass ?price= for scenarios",
            description = "`asOf=YYYY-MM-DD` selects the cost profile to compute against — the "
                    + "date chooses the costs, not the price.")
    public ResponseEntity<ApiResponse<ProfitabilityResponse>> profitability(
            @PathVariable Long id, @RequestParam(required = false) BigDecimal price,
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate asOf) {
        return ResponseEntity.ok(ApiResponse.success(
                service.computeProfitability(id, price,
                        asOf == null ? java.time.LocalDate.now() : asOf),
                "Profitability"));
    }

    @GetMapping("/profitability/trend")
    @Operation(summary = "Net-profit trend over time (daily snapshots) for the Profitability chart")
    public ResponseEntity<ApiResponse<java.util.List<com.priceintel.backend.dto.response.ProfitabilityTrendPoint>>> trend(
            @PathVariable Long id,
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(
                snapshotService.trend(id, from, to), "Profitability trend"));
    }
}
