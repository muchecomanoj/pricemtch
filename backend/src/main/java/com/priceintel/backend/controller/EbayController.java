package com.priceintel.backend.controller;

import java.util.List;

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
import com.priceintel.backend.service.EbayIngestionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * eBay-specific endpoints (Phase 8): ingest an item (fetch → store raw JSON →
 * store canonical product) and inspect stored raw payloads.
 */
@RestController
@RequestMapping("/api/v1/marketplaces/ebay")
@RequiredArgsConstructor
@Tag(name = "eBay Integration", description = "Ingest listings from the eBay Browse API and view raw source records")
public class EbayController {

    private static final String WRITE_ROLES = "hasAnyRole('ADMIN', 'MANAGER')";

    private final EbayIngestionService ingestionService;
    private final RawSourceRecordRepository rawRepository;

    @PostMapping("/items/{itemId}/ingest")
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Fetch an eBay item, store raw JSON, and persist the canonical product")
    public ResponseEntity<ApiResponse<ProductResponse>> ingest(@PathVariable String itemId) {
        ProductResponse product = ingestionService.ingestByItemId(itemId);
        return ResponseEntity.ok(ApiResponse.success(product, "Ingested eBay item " + itemId));
    }

    @GetMapping("/items/{itemId}/raw")
    @Operation(summary = "List the raw eBay JSON payloads stored for an item")
    public ResponseEntity<ApiResponse<List<RawSourceRecord>>> raw(@PathVariable String itemId) {
        List<RawSourceRecord> records =
                rawRepository.findBySourceAndExternalIdOrderByCreatedAtDesc("EBAY", itemId.trim());
        return ResponseEntity.ok(ApiResponse.success(records, "Raw source records"));
    }
}
