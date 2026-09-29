package com.priceintel.backend.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.priceintel.backend.dto.request.UpdateProfileRequest;
import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.dto.response.UserResponse;
import com.priceintel.backend.security.CustomUserDetails;
import com.priceintel.backend.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * The current user's own profile (Settings → Profile). Available to ANY
 * authenticated user (not just admins) to view/edit their own details.
 */
@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "My Profile", description = "View and update your own profile")
public class ProfileController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "Get my profile (name, phone, photo, role, status, last sign-in)")
    public ResponseEntity<ApiResponse<UserResponse>> me(@AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.getProfile(principal.getUserId()), "Profile"));
    }

    @PutMapping
    @Operation(summary = "Update my profile (first/last name, phone, photo). Email/role are admin-managed.")
    public ResponseEntity<ApiResponse<UserResponse>> update(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.updateProfile(principal.getUserId(), request), "Profile updated"));
    }
}
