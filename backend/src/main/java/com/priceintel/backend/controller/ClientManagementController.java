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

import com.priceintel.backend.dto.request.CreateTenantRequest;
import com.priceintel.backend.dto.request.UpdateTenantRequest;
import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.dto.response.PagedResponse;
import com.priceintel.backend.dto.response.TenantResponse;
import com.priceintel.backend.dto.response.TenantStatsResponse;
import com.priceintel.backend.service.OnboardingService;
import com.priceintel.backend.service.TenantService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * SUPER_ADMIN client (tenant) management.
 */
@RestController
@RequestMapping("/api/v1/admin/clients")
@RequiredArgsConstructor
@Tag(name = "SUPER_ADMIN · Clients", description = "Create and manage tenant (client) organizations")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class ClientManagementController {

    private final TenantService tenantService;
    private final OnboardingService onboardingService;

    @PostMapping
    @Operation(summary = "Create a client (tenant) — sends an activation email with a link + code")
    public ResponseEntity<ApiResponse<TenantResponse>> create(@Valid @RequestBody CreateTenantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(tenantService.createTenant(request), "Client created"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a client")
    public ResponseEntity<ApiResponse<TenantResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(tenantService.getTenant(id), "Client retrieved"));
    }

    @GetMapping
    @Operation(summary = "List / search clients")
    public ResponseEntity<ApiResponse<PagedResponse<TenantResponse>>> list(
            @RequestParam(required = false) String search,
            // The Clients screen sends its search box as "q". Only "search" was
            // read, so the text was silently ignored and every company came back.
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        String keyword = search != null && !search.isBlank() ? search : q;
        return ResponseEntity.ok(ApiResponse.success(
                tenantService.listTenants(keyword, page, size, sortBy, direction), "Clients retrieved"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a client's company profile")
    public ResponseEntity<ApiResponse<TenantResponse>> update(
            @PathVariable Long id, @Valid @RequestBody UpdateTenantRequest request) {
        return ResponseEntity.ok(ApiResponse.success(tenantService.updateTenant(id, request), "Client updated"));
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate a client")
    public ResponseEntity<ApiResponse<TenantResponse>> activate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(tenantService.activate(id), "Client activated"));
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a client")
    public ResponseEntity<ApiResponse<TenantResponse>> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(tenantService.deactivate(id), "Client deactivated"));
    }

    @PatchMapping("/{id}/suspend")
    @Operation(summary = "Suspend a client")
    public ResponseEntity<ApiResponse<TenantResponse>> suspend(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(tenantService.suspend(id), "Client suspended"));
    }

    @PatchMapping("/{id}/resume")
    @Operation(summary = "Resume a suspended client")
    public ResponseEntity<ApiResponse<TenantResponse>> resume(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(tenantService.resume(id), "Client resumed"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete a client")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        tenantService.softDelete(id);
        return ResponseEntity.ok(ApiResponse.success("Client deleted"));
    }

    @PostMapping("/{id}/resend-activation")
    @Operation(summary = "Re-send the activation email (link + verification code) to a pending client")
    public ResponseEntity<ApiResponse<Void>> resendActivation(@PathVariable Long id) {
        onboardingService.resendActivation(id);
        return ResponseEntity.ok(ApiResponse.success("Activation email re-sent"));
    }

    @GetMapping("/{id}/stats")
    @Operation(summary = "Client statistics")
    public ResponseEntity<ApiResponse<TenantStatsResponse>> stats(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(tenantService.getStats(id), "Client statistics"));
    }

    @GetMapping("/{id}/usage")
    @Operation(summary = "Client usage")
    public ResponseEntity<ApiResponse<TenantStatsResponse>> usage(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(tenantService.getStats(id), "Client usage"));
    }
}
