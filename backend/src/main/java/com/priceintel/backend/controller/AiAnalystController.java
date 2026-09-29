package com.priceintel.backend.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.priceintel.backend.dto.request.AnalystAskRequest;
import com.priceintel.backend.dto.response.AnalystResponse;
import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.service.impl.AiAnalystService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** AI Analyst — deterministic Q&A over stored pricing/margin/competitor data. */
@RestController
@RequestMapping("/api/v1/ai/analyst")
@RequiredArgsConstructor
@Tag(name = "AI Analyst", description = "Answers pricing/competitor/margin questions from stored evidence")
public class AiAnalystController {

    private final AiAnalystService service;

    @GetMapping("/suggestions")
    @Operation(summary = "Suggested starter questions")
    public ResponseEntity<ApiResponse<List<String>>> suggestions() {
        return ResponseEntity.ok(ApiResponse.success(service.suggestions(), "Suggestions"));
    }

    @PostMapping("/ask")
    @Operation(summary = "Ask a question; answered deterministically from stored data")
    public ResponseEntity<ApiResponse<AnalystResponse>> ask(@Valid @RequestBody AnalystAskRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.ask(request.getQuestion()), "Answer"));
    }
}
