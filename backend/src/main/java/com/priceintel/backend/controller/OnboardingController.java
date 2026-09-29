package com.priceintel.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.priceintel.backend.dto.request.ChoosePlanRequest;
import com.priceintel.backend.dto.request.CompleteProfileRequest;
import com.priceintel.backend.dto.request.SetPasswordRequest;
import com.priceintel.backend.dto.request.ValidateActivationRequest;
import com.priceintel.backend.dto.request.VerifyActivationCodeRequest;
import com.priceintel.backend.dto.response.ActivationCompleteResponse;
import com.priceintel.backend.dto.response.ActivationValidationResponse;
import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.service.OnboardingService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Public client-activation flow (no authentication — the emailed token is the
 * credential):
 * <pre>
 *   validate (link)  →  verify-code (from email)  →  set-password  →  account live
 * </pre>
 */
@RestController
@RequestMapping("/api/public/onboarding")
@RequiredArgsConstructor
@Tag(name = "Client Onboarding (Public)", description = "Activate a client account from the emailed link + code")
public class OnboardingController {

    private final OnboardingService onboardingService;

    @PostMapping("/validate")
    @Operation(summary = "Validate the activation link token (opens the verification page)")
    public ResponseEntity<ApiResponse<ActivationValidationResponse>> validate(
            @Valid @RequestBody ValidateActivationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                onboardingService.validate(request), "Activation link is valid"));
    }

    @PostMapping("/verify-code")
    @Operation(summary = "Verify the 6-digit code from the activation email")
    public ResponseEntity<ApiResponse<ActivationValidationResponse>> verifyCode(
            @Valid @RequestBody VerifyActivationCodeRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                onboardingService.verifyCode(request), "Code verified. You can now set your password."));
    }

    @PostMapping("/set-password")
    @Operation(summary = "Set the password (next: complete profile → choose plan → pay)")
    public ResponseEntity<ApiResponse<ActivationCompleteResponse>> setPassword(
            @Valid @RequestBody SetPasswordRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                onboardingService.setPassword(request), "Password set. Complete your company profile next."));
    }

    @PostMapping("/complete-profile")
    @Operation(summary = "Enter company/business details (incl. GST/Tax)")
    public ResponseEntity<ApiResponse<ActivationCompleteResponse>> completeProfile(
            @Valid @RequestBody CompleteProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                onboardingService.completeProfile(request), "Profile saved. Choose a plan next."));
    }

    @PostMapping("/choose-plan")
    @Operation(summary = "Select a subscription plan and billing cycle (MONTHLY/YEARLY)")
    public ResponseEntity<ApiResponse<ActivationCompleteResponse>> choosePlan(
            @Valid @RequestBody ChoosePlanRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                onboardingService.choosePlan(request), "Plan selected. Proceed to payment."));
    }
}
