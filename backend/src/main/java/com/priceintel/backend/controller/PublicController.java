package com.priceintel.backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.priceintel.backend.constants.LandingSection;
import com.priceintel.backend.dto.request.ContactMessageSubmission;
import com.priceintel.backend.dto.request.DemoRequestSubmission;
import com.priceintel.backend.dto.request.NewsletterSubscription;
import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.dto.response.PlanResponse;
import com.priceintel.backend.service.LandingContentService;
import com.priceintel.backend.service.PublicFormService;
import com.priceintel.backend.service.SubscriptionService;
import com.priceintel.backend.utils.CallerAddress;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Public landing-page content: subscription plans, editable marketing copy, and
 * the three forms a visitor can submit before they have an account
 * (see {@link SelfRegistrationController} for what happens after).
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Tag(name = "Landing Page (Public)", description = "Plans, page content and the public forms")
public class PublicController {

    private final SubscriptionService subscriptionService;
    private final LandingContentService landingContentService;
    private final PublicFormService publicFormService;

    // ── Plans ──────────────────────────────────────────────────────────────

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

    // ── Page content ───────────────────────────────────────────────────────

    @GetMapping("/landing")
    @Operation(summary = "All visible landing-page sections, keyed by section name")
    public ResponseEntity<ApiResponse<Map<String, Object>>> landing() {
        return ResponseEntity.ok(ApiResponse.success(landingContentService.publicContent(), "Landing content"));
    }

    @GetMapping("/features")
    @Operation(summary = "Platform features shown on the landing page")
    public ResponseEntity<ApiResponse<Object>> features() {
        return ResponseEntity.ok(ApiResponse.success(
                landingContentService.publicSection(LandingSection.FEATURES), "Features"));
    }

    @GetMapping("/faqs")
    @Operation(summary = "Frequently asked questions")
    public ResponseEntity<ApiResponse<Object>> faqs() {
        return ResponseEntity.ok(ApiResponse.success(
                landingContentService.publicSection(LandingSection.FAQS), "FAQs"));
    }

    @GetMapping("/testimonials")
    @Operation(summary = "Customer testimonials")
    public ResponseEntity<ApiResponse<Object>> testimonials() {
        return ResponseEntity.ok(ApiResponse.success(
                landingContentService.publicSection(LandingSection.TESTIMONIALS), "Testimonials"));
    }

    // ── Forms ──────────────────────────────────────────────────────────────

    @PostMapping("/request-demo")
    @Operation(summary = "Book a demo")
    public ResponseEntity<ApiResponse<Void>> requestDemo(@Valid @RequestBody DemoRequestSubmission body,
                                                        HttpServletRequest request) {
        publicFormService.submitDemoRequest(body, CallerAddress.of(request));
        return ResponseEntity.ok(ApiResponse.success(
                "Thanks — we have your request and will be in touch shortly."));
    }

    @PostMapping("/contact-us")
    @Operation(summary = "Send a message through the contact form")
    public ResponseEntity<ApiResponse<Void>> contact(@Valid @RequestBody ContactMessageSubmission body,
                                                     HttpServletRequest request) {
        publicFormService.submitContactMessage(body, CallerAddress.of(request));
        return ResponseEntity.ok(ApiResponse.success(
                "Thanks — your message has been sent. We usually reply within one working day."));
    }

    @PostMapping("/newsletter")
    @Operation(summary = "Subscribe to the newsletter")
    public ResponseEntity<ApiResponse<Void>> newsletter(@Valid @RequestBody NewsletterSubscription body,
                                                        HttpServletRequest request) {
        publicFormService.subscribe(body, CallerAddress.of(request));
        return ResponseEntity.ok(ApiResponse.success("You are subscribed. Thanks for signing up."));
    }

    @PostMapping("/newsletter/unsubscribe")
    @Operation(summary = "Leave the newsletter using the token from the email footer")
    public ResponseEntity<ApiResponse<Void>> unsubscribe(@RequestParam String token) {
        publicFormService.unsubscribe(token);
        // The same answer whether or not the token existed: a differing response
        // would let anyone test tokens to find out which are real.
        return ResponseEntity.ok(ApiResponse.success("You have been removed from the newsletter."));
    }
}
