package com.priceintel.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.priceintel.backend.dto.request.CheckoutRequest;
import com.priceintel.backend.dto.request.MockPaymentRequest;
import com.priceintel.backend.dto.request.RegisterSetPasswordRequest;
import com.priceintel.backend.dto.request.RegisterVerifyRequest;
import com.priceintel.backend.dto.request.SelfRegisterRequest;
import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.dto.response.CheckoutResponse;
import com.priceintel.backend.dto.response.SelfRegisterResponse;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.payment.StripeService;
import com.priceintel.backend.service.SelfRegistrationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Public landing-page self-registration:
 * <pre>
 *   register (plan + profile) → verify-code → set-password → checkout → paid → account created
 * </pre>
 * No tenant/user exists until payment succeeds. The {@code token} threads
 * through every step.
 */
@RestController
@RequestMapping("/api/public/register")
@RequiredArgsConstructor
@Tag(name = "Self Registration (Public)", description = "Landing-page signup: plan → profile → verify → password → pay")
public class SelfRegistrationController {

    private final SelfRegistrationService selfRegistrationService;
    private final StripeService stripeService;

    @PostMapping
    @Operation(summary = "Submit chosen plan + profile → emails a verification code")
    public ResponseEntity<ApiResponse<SelfRegisterResponse>> register(
            @Valid @RequestBody SelfRegisterRequest request) {
        SelfRegisterResponse response = selfRegistrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Verification code sent to your email"));
    }

    @PostMapping("/verify-code")
    @Operation(summary = "Verify the emailed 6-digit code")
    public ResponseEntity<ApiResponse<SelfRegisterResponse>> verifyCode(
            @Valid @RequestBody RegisterVerifyRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                selfRegistrationService.verifyCode(request), "Code verified. Set your password next."));
    }

    @PostMapping("/set-password")
    @Operation(summary = "Set the password → proceed to payment")
    public ResponseEntity<ApiResponse<SelfRegisterResponse>> setPassword(
            @Valid @RequestBody RegisterSetPasswordRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                selfRegistrationService.setPassword(request), "Password set. Proceed to payment."));
    }

    @PostMapping("/checkout")
    @Operation(summary = "Payment summary for the chosen plan (Stripe URL or MOCK)")
    public ResponseEntity<ApiResponse<CheckoutResponse>> checkout(@Valid @RequestBody CheckoutRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                selfRegistrationService.checkout(request.getToken()), "Checkout ready"));
    }

    @PostMapping("/mock-confirm")
    @Operation(summary = "Simulate a successful payment (only when Stripe is NOT configured)")
    public ResponseEntity<ApiResponse<SelfRegisterResponse>> mockConfirm(
            @Valid @RequestBody MockPaymentRequest request) {
        if (stripeService.isConfigured()) {
            throw new BadRequestException("Stripe is configured; use the real checkout + webhook flow");
        }
        return ResponseEntity.ok(ApiResponse.success(
                selfRegistrationService.completePayment(request.getToken()),
                "Payment simulated; your account is now active"));
    }
}
