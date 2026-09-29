package com.priceintel.backend.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.priceintel.backend.dto.request.TwoFactorCodeRequest;
import com.priceintel.backend.dto.request.TwoFactorResendRequest;
import com.priceintel.backend.dto.request.TwoFactorVerifyRequest;
import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.dto.response.AuthResponse;
import com.priceintel.backend.dto.response.TwoFactorEnableResponse;
import com.priceintel.backend.dto.response.TwoFactorSetupResponse;
import com.priceintel.backend.dto.response.TwoFactorStatusResponse;
import com.priceintel.backend.service.TwoFactorService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Two-factor authentication (TOTP / authenticator app).
 *
 * <p>status / setup / enable / disable require an authenticated session.
 * verify / resend are public — they run during login, when the caller only
 * holds a challenge token.</p>
 */
@RestController
@RequestMapping("/api/v1/auth/2fa")
@RequiredArgsConstructor
@Tag(name = "Two-Factor Auth", description = "TOTP enrollment, management, and login verification")
public class TwoFactorController {

    private final TwoFactorService twoFactorService;

    @GetMapping("/status")
    @Operation(summary = "Is 2FA enabled for the current user?")
    public ResponseEntity<ApiResponse<TwoFactorStatusResponse>> status() {
        return ResponseEntity.ok(ApiResponse.success(twoFactorService.status(), "Two-factor status"));
    }

    @PostMapping("/setup")
    @Operation(summary = "Begin enrollment — returns secret + otpauth URL for the QR code")
    public ResponseEntity<ApiResponse<TwoFactorSetupResponse>> setup() {
        return ResponseEntity.ok(ApiResponse.success(twoFactorService.setup(), "Scan the QR code, then confirm"));
    }

    @PostMapping("/enable")
    @Operation(summary = "Confirm enrollment with an authenticator code — returns backup codes")
    public ResponseEntity<ApiResponse<TwoFactorEnableResponse>> enable(
            @Valid @RequestBody TwoFactorCodeRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                twoFactorService.enable(request.getCode()),
                "Two-factor authentication enabled. Save your backup codes."));
    }

    @PostMapping("/disable")
    @Operation(summary = "Disable 2FA (requires a valid code)")
    public ResponseEntity<ApiResponse<TwoFactorStatusResponse>> disable(
            @Valid @RequestBody TwoFactorCodeRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                twoFactorService.disable(request.getCode()), "Two-factor authentication disabled"));
    }

    @PostMapping("/verify")
    @Operation(summary = "Complete login: verify the OTP with the challenge token (public)")
    public ResponseEntity<ApiResponse<AuthResponse>> verify(
            @Valid @RequestBody TwoFactorVerifyRequest request) {
        AuthResponse session = twoFactorService.verify(request.getCode(), request.getChallengeToken());
        return ResponseEntity.ok(ApiResponse.success(session, "Login successful"));
    }

    @PostMapping("/resend")
    @Operation(summary = "Re-validate the challenge (TOTP codes are device-generated; nothing is sent)")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> resend(
            @Valid @RequestBody TwoFactorResendRequest request) {
        twoFactorService.resend(request.getChallengeToken());
        return ResponseEntity.ok(ApiResponse.success(Map.of("ok", true), "Challenge is still valid"));
    }
}
