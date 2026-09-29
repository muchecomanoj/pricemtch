package com.priceintel.backend.dto.response;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A generated report: preview rows for on-screen display plus the full content
 * string (CSV or JSON) the frontend can download.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportResponse {
    private String reportType;
    private String format;
    private String filename;
    private String mimeType;
    private List<String> columns;
    private List<List<String>> rows;   // preview rows (may be capped)
    private int rowCount;              // total rows in the report
    private String content;            // full CSV/JSON, ready to download
    private Instant generatedAt;
}
