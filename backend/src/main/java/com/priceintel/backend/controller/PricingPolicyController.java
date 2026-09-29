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

import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.entity.PricingPolicy;
import com.priceintel.backend.service.impl.PricingPolicyService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * The bounds recommended prices must respect (FR-REC-002).
 *
 * <p>Until these exist, automatic publishing cannot responsibly be enabled:
 * there would be nothing between a bad recommendation and a live price.</p>
 */
@RestController
@RequestMapping("/api/v1/pricing-policies")
@RequiredArgsConstructor
@Tag(name = "Pricing Policies",
        description = "Floors, ceilings, change limits and cooldowns for recommended prices")
public class PricingPolicyController {

    private static final String WRITE_ROLES = "hasAnyRole('ADMIN', 'MANAGER')";

    private final PricingPolicyService service;

    @GetMapping
    @Operation(summary = "All pricing policies for this client")
    public ResponseEntity<ApiResponse<List<PricingPolicy>>> list() {
        return ResponseEntity.ok(ApiResponse.success(service.list(), "Pricing policies"));
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Create or update a pricing policy",
            description = "Scope it with `productId` and `marketplace`; leaving both out makes it "
                    + "the client-wide default. The most specific matching policy wins:\n\n"
                    + "`product + channel` → `product` → `client + channel` → `client`\n\n"
                    + "**Bounds:** `floorPrice` and `floorMarginPct` are both floors and the "
                    + "higher wins — a fixed floor cannot know today's costs, a margin floor "
                    + "cannot say \"never below what we paid\". `maxChangePct` and "
                    + "`maxChangeAmount` limit one cycle's movement, and the tighter applies; "
                    + "they stop a run of individually reasonable recommendations walking a price "
                    + "down over days. `cooldownHours` stops the system reacting to its own last "
                    + "move before the market has.\n\n"
                    + "`autoPublish` is refused unless at least one bound is set.")
    public ResponseEntity<ApiResponse<PricingPolicy>> save(@RequestBody PricingPolicy policy) {
        return ResponseEntity.ok(ApiResponse.success(service.save(policy), "Policy saved"));
    }

    @GetMapping("/effective")
    @Operation(summary = "The policy that would apply to a product on a channel",
            description = "Resolves the scope chain and returns the winner, or null when nothing "
                    + "is configured. Use it to show a product's actual bounds rather than making "
                    + "the user work out which rule applies.")
    public ResponseEntity<ApiResponse<PricingPolicy>> effective(
            @RequestParam Long productId,
            @RequestParam(required = false) String marketplace) {
        return ResponseEntity.ok(ApiResponse.success(
                service.resolve(productId, marketplace), "Effective policy"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Delete a pricing policy")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Policy deleted"));
    }
}
