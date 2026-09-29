package com.priceintel.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.priceintel.backend.dto.request.AlertRuleRequest;
import com.priceintel.backend.dto.response.AlertRuleResponse;
import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.service.impl.AlertRuleService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Alert rules (FR-ALERT-001) — the "Track price" feature. */
@RestController
@RequestMapping("/api/v1/alerts/rules")
@RequiredArgsConstructor
@Tag(name = "Alerts", description = "Create and manage product monitoring rules")
public class AlertRuleController {

    private static final String WRITE_ROLES = "hasAnyRole('ADMIN', 'MANAGER', 'SUPER_ADMIN')";

    private final AlertRuleService service;
    private final com.priceintel.backend.service.impl.AlertEvaluationService evaluationService;

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Create an alert rule (Track price)")
    public ResponseEntity<ApiResponse<AlertRuleResponse>> create(@Valid @RequestBody AlertRuleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.create(request), "Alert rule created"));
    }

    @PostMapping("/bulk")
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Create one alert rule across many products",
            description = "Send `productIds` plus the same condition, threshold and channels the "
                    + "single-product form collects.\n\n"
                    + "**Not all-or-nothing.** Two products of fifty already having this alert is "
                    + "not a reason to refuse the other forty-eight, so each product comes back "
                    + "with its own row — `CREATED`, `SKIPPED_DUPLICATE`, "
                    + "`SKIPPED_NO_COMPETITORS`, `NOT_FOUND` or `FAILED` — alongside the totals. "
                    + "Show the totals, and the rows for anything that was not created.\n\n"
                    + "A product with no competitor listings still gets a rule, flagged "
                    + "`cannotFireYet`, because competitors can be added later. Set "
                    + "`skipProductsWithoutCompetitors` to leave them out instead.\n\n"
                    + "Duplicate means the same product already has a rule with this condition, "
                    + "whatever its threshold — two rules on one condition both fire and simply "
                    + "double the noise. The existing rule's id comes back as `existingRuleId`.\n\n"
                    + "Limit: 500 products per request.")
    public ResponseEntity<ApiResponse<com.priceintel.backend.dto.response.BulkAlertRuleResponse>>
            createBulk(@Valid @RequestBody
                    com.priceintel.backend.dto.request.BulkAlertRuleRequest request) {
        var result = service.createBulk(request);
        return ResponseEntity.ok(ApiResponse.success(result,
                result.getCreated() + " created, " + result.getSkipped() + " skipped"));
    }

    @PostMapping("/bulk-delete")
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Delete many alert rules",
            description = "POST rather than DELETE because the ids travel in a body, which "
                    + "DELETE does not reliably carry. Rules belonging to another client are "
                    + "ignored; the count says how many were actually removed.")
    public ResponseEntity<ApiResponse<java.util.Map<String, Integer>>> deleteBulk(
            @RequestBody java.util.List<Long> ruleIds) {
        int deleted = service.deleteBulk(ruleIds);
        return ResponseEntity.ok(ApiResponse.success(
                java.util.Map.of("deleted", deleted), deleted + " alert rule(s) deleted"));
    }

    @PostMapping("/bulk-active")
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Pause or resume many alert rules",
            description = "`active=false` pauses: the rule and its history are kept and it simply "
                    + "never fires. Use this rather than delete for \"stop telling me about this "
                    + "for now\" — deleting throws the history away too.")
    public ResponseEntity<ApiResponse<java.util.Map<String, Integer>>> setActiveBulk(
            @RequestParam boolean active,
            @RequestBody java.util.List<Long> ruleIds) {
        int changed = service.setActiveBulk(ruleIds, active);
        return ResponseEntity.ok(ApiResponse.success(java.util.Map.of("changed", changed),
                changed + " alert rule(s) " + (active ? "resumed" : "paused")));
    }

    @org.springframework.web.bind.annotation.PatchMapping("/{id}/active")
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Pause or resume one alert rule")
    public ResponseEntity<ApiResponse<AlertRuleResponse>> setActive(
            @PathVariable Long id, @RequestParam boolean active) {
        return ResponseEntity.ok(ApiResponse.success(service.setActive(id, active),
                active ? "Alert rule resumed" : "Alert rule paused"));
    }

    @GetMapping
    @Operation(summary = "List alert rules for a product (or all tenant rules if productId omitted)")
    public ResponseEntity<ApiResponse<List<AlertRuleResponse>>> list(
            @RequestParam(required = false) Long productId) {
        List<AlertRuleResponse> rules = productId != null
                ? service.listByProduct(productId) : service.listForTenant();
        return ResponseEntity.ok(ApiResponse.success(rules, "Alert rules"));
    }

    @PostMapping("/evaluate")
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Run the alert engine now; fires notifications for any rules whose condition is met")
    public ResponseEntity<ApiResponse<java.util.Map<String, Integer>>> evaluate() {
        int fired = evaluationService.evaluateAll();
        return ResponseEntity.ok(ApiResponse.success(
                java.util.Map.of("fired", fired), "Evaluated alert rules"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Delete an alert rule")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Alert rule deleted"));
    }
}
