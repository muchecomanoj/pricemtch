package com.priceintel.backend.dto.response;

import java.time.LocalDateTime;

import com.priceintel.backend.constants.ImportFileType;
import com.priceintel.backend.constants.ImportStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Status / history view of an import job (also serves as the progress payload).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportJobResponse {

    private Long id;
    private String fileName;
    private ImportFileType fileType;
    private ImportStatus status;
    private int totalRows;
    private int processedRows;
    private int successCount;
    private int errorCount;
    private int duplicateCount;
    private int progressPercent;
    private String message;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
}
