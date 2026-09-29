package com.priceintel.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.priceintel.backend.dto.request.ChangePasswordRequest;
import com.priceintel.backend.dto.request.ForgotPasswordRequest;
import com.priceintel.backend.dto.request.LoginRequest;
import com.priceintel.backend.dto.request.LogoutRequest;
import com.priceintel.backend.dto.request.RefreshTokenRequest;
import com.priceintel.backend.dto.request.ResetPasswordRequest;
import com.priceintel.backend.dto.request.VerifyResetCodeRequest;
import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.dto.response.AuthResponse;
import com.priceintel.backend.dto.response.UserResponse;
import com.priceintel.backend.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Authentication & account endpoints. All return the common {@link ApiResponse}
 * envelope via {@link ResponseEntity}.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, login, tokens, and password management")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Authenticate and receive access & refresh tokens")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a valid refresh token for a new token pair")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Token refreshed"));
    }

    @PostMapping("/logout")
    @Operation(summary = "Invalidate a refresh token")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok(ApiResponse.success("Logout successful"));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change the current user's password (requires authentication)")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully"));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Step 1 — email a 6-digit reset code to the registered address")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success("A verification code has been sent to your email."));
    }

    @PostMapping("/verify-reset-code")
    @Operation(summary = "Step 2 — verify the emailed code before resetting the password")
    public ResponseEntity<ApiResponse<Void>> verifyResetCode(@Valid @RequestBody VerifyResetCodeRequest request) {
        authService.verifyResetCode(request);
        return ResponseEntity.ok(ApiResponse.success("Code verified. You can now set a new password."));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Step 3 — set the new password using the verified code")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password reset successfully. Please log in."));
    }

    @GetMapping("/me")
    @Operation(summary = "Get the currently authenticated user's profile")
    public ResponseEntity<ApiResponse<UserResponse>> me() {
        UserResponse user = authService.getCurrentUser();
        return ResponseEntity.ok(ApiResponse.success(user, "Current user"));
    }
}
