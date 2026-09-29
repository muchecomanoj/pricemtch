package com.priceintel.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.priceintel.backend.constants.MarketplaceProvider;
import com.priceintel.backend.dto.request.MarketplaceConfigRequest;
import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.dto.response.MarketplaceStatusResponse;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.service.impl.MarketplaceConfigService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * SUPER_ADMIN configuration of the platform's marketplace integrations
 * (Settings → Marketplaces). Credentials are stored encrypted and never echoed.
 */
@RestController
@RequestMapping("/api/v1/admin/marketplaces")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "SUPER_ADMIN · Marketplaces", description = "Configure and test Amazon / eBay / Keepa integrations")
public class MarketplaceConfigController {

    private final MarketplaceConfigService service;

    @GetMapping
    @Operation(summary = "List marketplaces with status, capabilities and masked config")
    public ResponseEntity<ApiResponse<List<MarketplaceStatusResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(service.list(), "Marketplaces"));
    }

    @PutMapping("/{code}")
    @Operation(summary = "Save a marketplace's credentials + enabled flag")
    public ResponseEntity<ApiResponse<MarketplaceStatusResponse>> save(
            @PathVariable String code, @Valid @RequestBody MarketplaceConfigRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                service.save(parse(code), request), "Marketplace configuration saved"));
    }

    @PostMapping("/{code}/test")
    @Operation(summary = "Test the marketplace connection using the stored credentials")
    public ResponseEntity<ApiResponse<MarketplaceStatusResponse>> test(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.success(
                service.testConnection(parse(code)), "Connection test complete"));
    }

    private MarketplaceProvider parse(String code) {
        try {
            return MarketplaceProvider.valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown marketplace: " + code);
        }
    }
}
