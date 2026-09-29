package com.priceintel.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.priceintel.backend.dto.request.SearchRequest;
import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.dto.response.PagedResponse;
import com.priceintel.backend.dto.response.SearchHistoryResponse;
import com.priceintel.backend.dto.response.SearchResponse;
import com.priceintel.backend.service.SearchService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Product search endpoints (Phase 5). Searches ONLY the local database using a
 * priority waterfall, and returns a full search trace. Any authenticated user
 * may search.
 */
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Local product search (identifier/SKU/title/URL waterfall) with trace and history")
public class SearchController {

    private final SearchService searchService;

    @PostMapping
    @Operation(summary = "Search products by ASIN/GTIN/EAN/UPC/MPN/SKU/title/URL (priority order)")
    public ResponseEntity<ApiResponse<SearchResponse>> search(@Valid @RequestBody SearchRequest request) {
        SearchResponse response = searchService.search(request);
        String message = response.getTotalMatches() > 0
                ? "Matched at stage " + response.getMatchedStage()
                : "No products matched";
        return ResponseEntity.ok(ApiResponse.success(response, message));
    }

    @GetMapping("/history")
    @Operation(summary = "List recent searches (most recent first)")
    public ResponseEntity<ApiResponse<PagedResponse<SearchHistoryResponse>>> history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(searchService.getHistory(page, size), "Search history"));
    }
}
