package com.priceintel.backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.priceintel.backend.constants.RecommendationStatus;
import com.priceintel.backend.dto.request.RecommendationRequest;
import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.dto.response.RecommendationResponse;
import com.priceintel.backend.service.impl.RecommendationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Price recommendations (FR-REC-001/002): generate, list, approve, publish. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Price Recommendations", description = "Generate and approve recommended prices")
public class RecommendationController {

    private static final String WRITE_ROLES = "hasAnyRole('ADMIN', 'MANAGER', 'SUPER_ADMIN')";

    private final RecommendationService service;
    private final com.priceintel.backend.service.impl.IdempotencyService idempotency;

    @PostMapping("/products/{id}/price-recommendations")
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Generate a price recommendation from competitor prices + cost profile")
    public ResponseEntity<ApiResponse<RecommendationResponse>> generate(
            @PathVariable Long id, @RequestBody(required = false) RecommendationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.generate(id, request), "Recommendation generated"));
    }

    @GetMapping("/products/{id}/price-recommendations")
    @Operation(summary = "List a product's recommendations (newest first)")
    public ResponseEntity<ApiResponse<List<RecommendationResponse>>> list(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.list(id), "Recommendations"));
    }

    @GetMapping("/recommendations")
    @Operation(summary = "Tenant-wide recommendations (paginated) for the Recommendations page")
    public ResponseEntity<ApiResponse<Page<RecommendationResponse>>> listAll(
            @RequestParam(required = false) RecommendationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(ApiResponse.success(
                service.listAll(status, pageable), "Recommendations"));
    }

    @PostMapping("/recommendations/generate")
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Generate DRAFT recommendations for every product that has enough data")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> generateAll() {
        int created = service.generateAll();
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("created", created), "Generated " + created + " recommendation(s)"));
    }

    @PostMapping("/recommendations/{id}/submit")
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Submit a draft for approval",
            description = "Moves DRAFT to PENDING_APPROVAL so an approver can review it. Optional — "
                    + "a draft can still be approved directly. List the queue with "
                    + "GET /recommendations?status=PENDING_APPROVAL.")
    public ResponseEntity<ApiResponse<RecommendationResponse>> submit(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                service.submitForApproval(id), "Submitted for approval"));
    }

    @PostMapping("/recommendations/{id}/approve")
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Approve a recommendation",
            description = "Accepts a recommendation in DRAFT or PENDING_APPROVAL.")
    public ResponseEntity<ApiResponse<RecommendationResponse>> approve(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.approve(id), "Approved"));
    }

    @PostMapping("/recommendations/{id}/publish")
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Publish an approved recommendation (marks published; no auto marketplace push)",
            description = "Idempotent: a repeat returns the original result rather than failing "
                    + "because the recommendation is no longer APPROVED.")
    public ResponseEntity<ApiResponse<RecommendationResponse>> publish(
            @PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestHeader(
                    value = "Idempotency-Key", required = false) String idempotencyKey) {
        // Publishing changes the product's selling price, and the state machine
        // rejects a second attempt — so a network retry after a successful
        // publish would surface as "cannot publish in state PUBLISHED" when the
        // action had in fact worked. Replaying the original answer is the honest
        // response to a repeated request.
        RecommendationResponse published = idempotency.execute(
                "PUBLISH_RECOMMENDATION", idempotencyKey, String.valueOf(id),
                RecommendationResponse.class, () -> service.publish(id));
        return ResponseEntity.ok(ApiResponse.success(published, "Published"));
    }

    @PostMapping("/recommendations/{id}/rollback")
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Undo a published price, restoring what it replaced (FR-REC-002)",
            description = "Restores the exact selling price this recommendation overwrote, "
                    + "captured at publish. Deliberately not a fresh recommendation — that would "
                    + "re-derive the same answer from the same data.\n\n"
                    + "The record stays marked as having been live rather than reverting to draft, "
                    + "because an audit needs to see that it was published and undone.\n\n"
                    + "Refused when the recommendation was never published, was already rolled "
                    + "back, or has no earlier price recorded.")
    public ResponseEntity<ApiResponse<RecommendationResponse>> rollback(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                service.rollback(id), "Rolled back to the previous price"));
    }

    @PostMapping("/recommendations/{id}/reject")
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Reject a recommendation")
    public ResponseEntity<ApiResponse<RecommendationResponse>> reject(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.reject(id), "Rejected"));
    }
}
