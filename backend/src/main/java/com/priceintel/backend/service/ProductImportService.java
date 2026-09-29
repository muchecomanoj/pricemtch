package com.priceintel.backend.service;

import org.springframework.web.multipart.MultipartFile;

import com.priceintel.backend.dto.response.ImportJobResponse;
import com.priceintel.backend.dto.response.ImportPreviewResponse;
import com.priceintel.backend.dto.response.PagedResponse;

/**
 * Bulk product import use cases (Phase 4).
 */
public interface ProductImportService {

    /** Parse the file and return detected columns, suggested mapping, and samples. */
    ImportPreviewResponse preview(MultipartFile file);

    /** Create an import job and start background processing. */
    ImportJobResponse startImport(MultipartFile file, String mappingJson);

    ImportJobResponse getStatus(Long jobId);

    PagedResponse<ImportJobResponse> getHistory(int page, int size);

    /** Build the row-level error report as CSV bytes. */
    byte[] exportErrorReport(Long jobId);
}
