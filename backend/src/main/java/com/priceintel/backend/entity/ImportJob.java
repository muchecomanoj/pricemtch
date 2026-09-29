package com.priceintel.backend.entity;

import java.time.LocalDateTime;

import com.priceintel.backend.constants.ImportFileType;
import com.priceintel.backend.constants.ImportStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One bulk-import run. Acts as both the import history record and the live
 * progress tracker (updated as rows are processed in the background).
 */
@Entity
@Table(name = "import_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportJob extends BaseEntity {

    /**
     * The client this import belongs to, captured on the request thread.
     *
     * <p>Processing happens asynchronously, and the tenant is held in a
     * ThreadLocal that does not cross onto the worker thread — so without
     * recording it here, every imported product was created with no owner and
     * became invisible to the person who uploaded the file.</p>
     */
    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "file_name", nullable = false, length = 300)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 10)
    private ImportFileType fileType;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ImportStatus status = ImportStatus.PENDING;

    @Builder.Default
    @Column(name = "total_rows", nullable = false)
    private int totalRows = 0;

    @Builder.Default
    @Column(name = "processed_rows", nullable = false)
    private int processedRows = 0;

    @Builder.Default
    @Column(name = "success_count", nullable = false)
    private int successCount = 0;

    @Builder.Default
    @Column(name = "error_count", nullable = false)
    private int errorCount = 0;

    @Builder.Default
    @Column(name = "duplicate_count", nullable = false)
    private int duplicateCount = 0;

    /** The column-mapping actually used (JSON), for auditability. */
    @Column(name = "mapping_json", length = 2000)
    private String mappingJson;

    @Column(length = 1000)
    private String message;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    /** Percentage 0-100 based on processed vs total rows. */
    public int getProgressPercent() {
        if (totalRows <= 0) {
            return status == ImportStatus.PENDING ? 0 : 100;
        }
        return (int) Math.floor((processedRows * 100.0) / totalRows);
    }
}
