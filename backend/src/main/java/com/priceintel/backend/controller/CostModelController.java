package com.priceintel.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.priceintel.backend.dto.request.CostModelRequest;
import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.dto.response.CostModelResponse;
import com.priceintel.backend.service.impl.CostModelService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Tenant default cost model (Cost Management page). */
@RestController
@RequestMapping("/api/v1/cost-model")
@RequiredArgsConstructor
@Tag(name = "Cost Model", description = "Tenant-wide default cost model")
public class CostModelController {

    private static final String WRITE_ROLES = "hasAnyRole('ADMIN', 'MANAGER', 'FINANCE', 'SUPER_ADMIN')";

    private final CostModelService service;

    @GetMapping
    @Operation(summary = "Get the tenant's cost model")
    public ResponseEntity<ApiResponse<CostModelResponse>> get() {
        return ResponseEntity.ok(ApiResponse.success(service.get(), "Cost model"));
    }

    @PutMapping
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Create/update the tenant's cost model")
    public ResponseEntity<ApiResponse<CostModelResponse>> save(@Valid @RequestBody CostModelRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.save(request), "Cost model saved"));
    }
}
