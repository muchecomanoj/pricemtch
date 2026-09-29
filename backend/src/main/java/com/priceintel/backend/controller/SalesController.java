package com.priceintel.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.dto.response.SalesResponse;
import com.priceintel.backend.service.impl.SalesService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Product sales (FR-SALES): owned (actual) vs competitor (estimated). */
@RestController
@RequestMapping("/api/v1/products/{id}")
@RequiredArgsConstructor
@Tag(name = "Product Sales", description = "Owned-account sales and competitor sales estimates")
public class SalesController {

    private final SalesService service;

    @GetMapping("/sales")
    @Operation(summary = "Get owned (actual) and estimated competitor sales for a product")
    public ResponseEntity<ApiResponse<SalesResponse>> sales(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.getSales(id), "Sales"));
    }
}
