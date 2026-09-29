package com.priceintel.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.priceintel.backend.dto.request.MatchRequest;
import com.priceintel.backend.dto.response.AiExecutionResponse;
import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.dto.response.MatchResponse;
import com.priceintel.backend.dto.response.PagedResponse;
import com.priceintel.backend.service.MatchService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * AI product matching endpoints (Phase 9). Uses OpenAI when configured; falls
 * back to the deterministic rules engine otherwise. Returns structured JSON.
 */
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI Matching", description = "Compare a product to a candidate and return a structured match result")
public class MatchController {

    private static final String MATCH_ROLES =
            "hasAnyRole('ADMIN', 'ANALYST')";

    private final MatchService matchService;

    @PostMapping("/match")
    @PreAuthorize(MATCH_ROLES)
    @Operation(summary = "Match a product against a candidate (returns score, decision, conflicts, etc.)")
    public ResponseEntity<ApiResponse<MatchResponse>> match(@Valid @RequestBody MatchRequest request) {
        MatchResponse result = matchService.match(request);
        return ResponseEntity.ok(ApiResponse.success(result, "Match evaluated"));
    }

    @GetMapping("/executions")
    @Operation(summary = "List AI execution audit log (provider, model, tokens, decision)")
    public ResponseEntity<ApiResponse<PagedResponse<AiExecutionResponse>>> executions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(matchService.getExecutions(page, size), "AI executions"));
    }
}
