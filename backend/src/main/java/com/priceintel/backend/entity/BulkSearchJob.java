package com.priceintel.backend.entity;

import java.time.LocalDateTime;

import com.priceintel.backend.constants.ImportFileType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One uploaded file of products to look for on the marketplaces (§13.1).
 *
 * <p>Distinct from a product import, which creates products and never calls a
 * marketplace. This calls marketplaces and creates nothing: every result is
 * saved to the catalogue by hand, because a bulk run over an unfamiliar list is
 * where a wrong match is least likely to be noticed.</p>
 */
@Entity
@Table(name = "bulk_search_jobs",
        indexes = @Index(name = "idx_bulk_search_tenant", columnList = "tenant_id, created_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkSearchJob extends BaseEntity {

    /** Job status. Mirrors the import vocabulary so both screens read alike. */
    public enum Status {
        PENDING, PROCESSING, COMPLETED, COMPLETED_WITH_ERRORS, FAILED
    }

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "file_name")
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", length = 10)
    private ImportFileType fileType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private Status status = Status.PENDING;

    /**
     * Whether the model ruled on each row's results.
     *
     * <p>Recorded on the job because it changes both how long the run takes and
     * how far the results can be trusted — and someone reading this months later
     * needs to know which it was.</p>
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean judge = true;

    @Column(name = "destination_country", length = 2)
    private String destinationCountry;

    @Column(name = "destination_postal_code", length = 20)
    private String destinationPostalCode;

    @Column(name = "total_rows", nullable = false)
    @Builder.Default
    private int totalRows = 0;

    @Column(name = "processed_rows", nullable = false)
    @Builder.Default
    private int processedRows = 0;

    /** Rows where at least one listing was judged to be the product. */
    @Column(name = "found_rows", nullable = false)
    @Builder.Default
    private int foundRows = 0;

    /** Rows searched successfully that matched nothing — a result, not a failure. */
    @Column(name = "empty_rows", nullable = false)
    @Builder.Default
    private int emptyRows = 0;

    @Column(name = "error_rows", nullable = false)
    @Builder.Default
    private int errorRows = 0;

    @Column(length = 500)
    private String message;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
}
