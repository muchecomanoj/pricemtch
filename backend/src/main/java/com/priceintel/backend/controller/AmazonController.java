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
import com.priceintel.backend.service.AmazonIngestionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Amazon-specific endpoints (Phase 7): ingest an ASIN (fetch → store raw JSON →
 * store canonical product) and inspect the stored raw payloads.
 */
@RestController
@RequestMapping("/api/v1/marketplaces/amazon")
@RequiredArgsConstructor
@Tag(name = "Amazon Integration", description = "Ingest products from Amazon SP-API and view raw source records")
public class AmazonController {

    private static final String WRITE_ROLES = "hasAnyRole('ADMIN', 'MANAGER')";

    private final AmazonIngestionService ingestionService;
    private final RawSourceRecordRepository rawRepository;

    @PostMapping("/products/{asin}/ingest")
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Fetch an ASIN from Amazon, store raw JSON, and persist the canonical product")
    public ResponseEntity<ApiResponse<ProductResponse>> ingest(@PathVariable String asin) {
        ProductResponse product = ingestionService.ingestByAsin(asin);
        return ResponseEntity.ok(ApiResponse.success(product, "Ingested ASIN " + asin));
    }

    @GetMapping("/products/{asin}/raw")
    @Operation(summary = "List the raw Amazon JSON payloads stored for an ASIN")
    public ResponseEntity<ApiResponse<List<RawSourceRecord>>> raw(@PathVariable String asin) {
        // Containment, not equality: competitive pricing is fetched in batches
        // of up to 20 ASINs and stored under the whole list.
        List<RawSourceRecord> records =
                rawRepository.findForExternalId("AMAZON", asin.trim().toUpperCase());
        return ResponseEntity.ok(ApiResponse.success(records, "Raw source records"));
    }
}
