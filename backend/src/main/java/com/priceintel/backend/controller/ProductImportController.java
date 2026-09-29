package com.priceintel.backend.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.dto.response.ImportJobResponse;
import com.priceintel.backend.dto.response.ImportPreviewResponse;
import com.priceintel.backend.dto.response.PagedResponse;
import com.priceintel.backend.service.ProductImportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Bulk product import endpoints (Phase 4). Uploads are CSV or .xlsx.
 * Imports run in the background; poll the status endpoint for progress.
 */
@RestController
@RequestMapping("/api/v1/products/imports")
@RequiredArgsConstructor
@Tag(name = "Product Import", description = "Bulk import products from CSV / Excel with preview, progress, and error report")
public class ProductImportController {

    private static final String WRITE_ROLES = "hasAnyRole('ADMIN', 'MANAGER')";

    private final ProductImportService importService;

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Preview an upload: detected columns, suggested mapping, sample rows")
    public ResponseEntity<ApiResponse<ImportPreviewResponse>> preview(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success(importService.preview(file), "Preview generated"));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Start an import (runs in background). Optional 'mapping' is a JSON field->column map")
    public ResponseEntity<ApiResponse<ImportJobResponse>> startImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "mapping", required = false) String mapping) {
        ImportJobResponse job = importService.startImport(file, mapping);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(job, "Import accepted and processing in background"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get import status and progress")
    public ResponseEntity<ApiResponse<ImportJobResponse>> status(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(importService.getStatus(id), "Import status"));
    }

    @GetMapping
    @Operation(summary = "List import history (most recent first)")
    public ResponseEntity<ApiResponse<PagedResponse<ImportJobResponse>>> history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success(importService.getHistory(page, size), "Import history"));
    }

    @GetMapping("/{id}/errors")
    @Operation(summary = "Download the row-level error report as CSV")
    public ResponseEntity<byte[]> errorReport(@PathVariable Long id) {
        byte[] csv = importService.exportErrorReport(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=import-" + id + "-errors.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }
}
