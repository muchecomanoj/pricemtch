package com.priceintel.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.priceintel.backend.constants.AccessType;
import com.priceintel.backend.constants.UserStatus;
import com.priceintel.backend.dto.request.CreateUserRequest;
import com.priceintel.backend.dto.request.ResetUserPasswordRequest;
import com.priceintel.backend.dto.request.UpdateUserRequest;
import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.dto.response.PagedResponse;
import com.priceintel.backend.dto.response.UserResponse;
import com.priceintel.backend.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Tenant user management (Client / Tenant Administrator). Every action is
 * scoped to the caller's own tenant — cross-tenant access is impossible.
 * Requires ADMIN access type.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "Tenant-scoped user CRUD by the Client (Tenant Administrator)")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    @PostMapping
    @Operation(summary = "Create a user in your organization (assign an access type)")
    public ResponseEntity<ApiResponse<UserResponse>> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(userService.createUser(request), "User created successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user in your organization")
    public ResponseEntity<ApiResponse<UserResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUser(id), "User retrieved"));
    }

    @GetMapping
    @Operation(summary = "List / search users in your organization")
    public ResponseEntity<ApiResponse<PagedResponse<UserResponse>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) AccessType accessType,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.listUsers(search, accessType, status, page, size, sortBy, direction),
                "Users retrieved"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a user")
    public ResponseEntity<ApiResponse<UserResponse>> update(
            @PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.success(userService.updateUser(id, request), "User updated"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete a user")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        userService.softDeleteUser(id);
        return ResponseEntity.ok(ApiResponse.success("User deleted successfully"));
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate a user")
    public ResponseEntity<ApiResponse<UserResponse>> activate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(userService.activateUser(id), "User activated"));
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a user")
    public ResponseEntity<ApiResponse<UserResponse>> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(userService.deactivateUser(id), "User deactivated"));
    }

    @PatchMapping("/{id}/reset-password")
    @Operation(summary = "Reset a user's password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @PathVariable Long id, @Valid @RequestBody ResetUserPasswordRequest request) {
        userService.resetPassword(id, request);
        return ResponseEntity.ok(ApiResponse.success("Password reset successfully"));
    }
}
