package com.priceintel.backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.dto.response.PlanResponse;
import com.priceintel.backend.service.SubscriptionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Public landing-page content: subscription plans and platform features. These
 * are what the client sees before choosing a plan and self-registering
 * (see {@link SelfRegistrationController}).
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Tag(name = "Landing Page (Public)", description = "Plans & features shown on the landing page")
public class PublicController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/subscription-plans")
    @Operation(summary = "List active subscription plans")
    public ResponseEntity<ApiResponse<List<PlanResponse>>> plans() {
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.listActivePlans(), "Subscription plans"));
    }

    @GetMapping("/pricing")
    @Operation(summary = "Pricing (active plans)")
    public ResponseEntity<ApiResponse<List<PlanResponse>>> pricing() {
        return ResponseEntity.ok(ApiResponse.success(subscriptionService.listActivePlans(), "Pricing"));
    }

    @GetMapping("/features")
    @Operation(summary = "Platform features")
    public ResponseEntity<ApiResponse<List<Map<String, String>>>> features() {
        List<Map<String, String>> features = List.of(
                Map.of("title", "Product Intelligence", "description", "Search products by SKU, ASIN, EAN, UPC, MPN"),
                Map.of("title", "Competitor Pricing", "description", "Track prices across Amazon, eBay, and the web"),
                Map.of("title", "AI Matching", "description", "AI-assisted product matching with confidence scores"),
                Map.of("title", "Profitability", "description", "Fees, margin, ROI, and break-even analysis"),
                Map.of("title", "Multi-Tenant SaaS", "description", "Secure, isolated workspace per organization"));
        return ResponseEntity.ok(ApiResponse.success(features, "Features"));
    }
}
