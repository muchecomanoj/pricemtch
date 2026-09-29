package com.priceintel.backend.dto.response;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Preview of an uploaded file BEFORE importing: detected columns, the suggested
 * column mapping, a few sample rows, and any warnings.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportPreviewResponse {

    private String fileName;
    private String fileType;
    private int totalRows;
    private List<String> detectedColumns;
    private Map<String, String> suggestedMapping;
    private List<Map<String, String>> sampleRows;
    private List<String> warnings;
}
