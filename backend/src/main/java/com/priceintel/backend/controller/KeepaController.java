package com.priceintel.backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.dto.response.ProductResponse;
import com.priceintel.backend.entity.RawSourceRecord;
import com.priceintel.backend.repository.RawSourceRecordRepository;
import com.priceintel.backend.service.KeepaIngestionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Amazon product & price data via Keepa (the Amazon data source). Ingest an
 * ASIN (fetch → store raw JSON → persist canonical product), read current
 * price + history, or inspect stored raw payloads.
 */
@RestController
@RequestMapping("/api/v1/marketplaces/amazon/keepa")
@RequiredArgsConstructor
@Tag(name = "Amazon (Keepa)", description = "Ingest Amazon products and prices from the Keepa API")
public class KeepaController {

    private static final String WRITE_ROLES = "hasAnyRole('ADMIN', 'MANAGER')";

    private final KeepaIngestionService ingestionService;
    private final RawSourceRecordRepository rawRepository;

    @PostMapping("/products/{asin}/ingest")
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Fetch an ASIN from Keepa, store raw JSON, and persist the canonical product")
    public ResponseEntity<ApiResponse<ProductResponse>> ingest(@PathVariable String asin) {
        ProductResponse product = ingestionService.ingestByAsin(asin);
        return ResponseEntity.ok(ApiResponse.success(product, "Ingested ASIN " + asin + " via Keepa"));
    }

    @GetMapping("/products/{asin}/price")
    @Operation(summary = "Current price and recent price history for an ASIN (from Keepa)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> price(@PathVariable String asin) {
        return ResponseEntity.ok(ApiResponse.success(
                ingestionService.priceByAsin(asin), "Keepa price for ASIN " + asin));
    }

    @GetMapping("/products/{asin}/raw")
    @Operation(summary = "List the raw Keepa JSON payloads stored for an ASIN")
    public ResponseEntity<ApiResponse<List<RawSourceRecord>>> raw(@PathVariable String asin) {
        List<RawSourceRecord> records = rawRepository
                .findBySourceAndExternalIdOrderByCreatedAtDesc("AMAZON", asin.trim().toUpperCase());
        return ResponseEntity.ok(ApiResponse.success(records, "Raw source records"));
    }
}
