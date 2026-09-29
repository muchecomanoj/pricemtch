package com.priceintel.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.priceintel.backend.dto.request.EmailConfigRequest;
import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.dto.response.EmailConfigResponse;
import com.priceintel.backend.service.impl.TenantEmailConfigService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Per-tenant SMTP settings (Settings → Email). */
@RestController
@RequestMapping("/api/v1/settings/email-config")
@RequiredArgsConstructor
@Tag(name = "Email (SMTP) Settings", description = "Per-tenant dynamic SMTP configuration")
public class EmailConfigController {

    private static final String ADMIN_ROLES = "hasAnyRole('ADMIN', 'SUPER_ADMIN')";

    private final TenantEmailConfigService service;

    @GetMapping
    @Operation(summary = "Get the tenant's SMTP settings (password never returned)")
    public ResponseEntity<ApiResponse<EmailConfigResponse>> get() {
        return ResponseEntity.ok(ApiResponse.success(service.get(), "Email config"));
    }

    @PutMapping
    @PreAuthorize(ADMIN_ROLES)
    @Operation(summary = "Create/update the tenant's SMTP settings")
    public ResponseEntity<ApiResponse<EmailConfigResponse>> save(@Valid @RequestBody EmailConfigRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.save(request), "Email config saved"));
    }

    @PostMapping("/test")
    @PreAuthorize(ADMIN_ROLES)
    @Operation(summary = "Test the SMTP connection with the saved settings")
    public ResponseEntity<ApiResponse<EmailConfigResponse>> test() {
        return ResponseEntity.ok(ApiResponse.success(service.test(), "SMTP test complete"));
    }
}
