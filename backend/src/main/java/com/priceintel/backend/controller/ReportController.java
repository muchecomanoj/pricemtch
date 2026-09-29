package com.priceintel.backend.controller;

import java.nio.charset.StandardCharsets;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.priceintel.backend.dto.request.ReportRequest;
import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.dto.response.ReportResponse;
import com.priceintel.backend.service.impl.ReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Competitor-price reports (Reports page). */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Generate competitor-price reports (CSV/JSON)")
public class ReportController {

    private final ReportService service;

    @PostMapping("/generate")
    @Operation(summary = "Generate a report; returns preview rows + full downloadable content")
    public ResponseEntity<ApiResponse<ReportResponse>> generate(@RequestBody ReportRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.generate(request), "Report generated"));
    }

    @GetMapping("/download")
    @Operation(summary = "Download the report file directly (CSV/JSON) with the given filters")
    public ResponseEntity<byte[]> download(
            @RequestParam(defaultValue = "CSV") String format,
            @RequestParam(required = false) String marketplace,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate to) {
        ReportResponse r = service.generate(ReportRequest.builder()
                .format(format).marketplace(marketplace).category(category).from(from).to(to).build());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + r.getFilename() + "\"")
                .contentType(MediaType.parseMediaType(r.getMimeType()))
                .body(r.getContent().getBytes(StandardCharsets.UTF_8));
    }
}
