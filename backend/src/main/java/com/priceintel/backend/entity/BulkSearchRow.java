package com.priceintel.backend.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One line of an uploaded file, and what the marketplaces said about it.
 *
 * <p>The input columns are kept verbatim so a result can always be traced back
 * to the line that produced it — and so a row that found nothing can be
 * re-examined without the original file.</p>
 */
@Entity
@Table(name = "bulk_search_rows",
        indexes = @Index(name = "idx_bulk_row_job", columnList = "job_id, row_number"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkSearchRow extends BaseEntity {

    /** {@code PENDING} until searched; then the search's own outcome. */
    public enum Status {
        PENDING, FOUND, NONE_MATCHED, NO_LISTINGS, NOT_JUDGED, ERROR
    }

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    // ---- what the file asked for ----

    @Column(name = "input_sku", length = 100)
    private String inputSku;

    @Column(name = "input_title", length = 500)
    private String inputTitle;

    @Column(name = "input_brand", length = 150)
    private String inputBrand;

    @Column(name = "input_asin", length = 20)
    private String inputAsin;

    @Column(name = "input_upc", length = 20)
    private String inputUpc;

    @Column(name = "input_ean", length = 20)
    private String inputEan;

    @Column(name = "input_gtin", length = 20)
    private String inputGtin;

    @Column(name = "input_mpn", length = 100)
    private String inputMpn;

    // ---- what happened ----

    @Column(name = "resolved_query", length = 500)
    private String resolvedQuery;

    @Column(name = "resolved_identifier_type", length = 20)
    private String resolvedIdentifierType;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "match_count", nullable = false)
    @Builder.Default
    private int matchCount = 0;

    @Column(name = "rejected_count", nullable = false)
    @Builder.Default
    private int rejectedCount = 0;

    /**
     * The judged listings, as the JSON the single-search endpoint returns.
     *
     * <p>Stored rather than re-fetched: rebuilding the results page by searching
     * again would spend marketplace quota a second time and could return
     * different prices, so the page would disagree with the job that made it.</p>
     */
    @Column(name = "matches_json", columnDefinition = "TEXT")
    private String matchesJson;

    @Column(name = "rejected_json", columnDefinition = "TEXT")
    private String rejectedJson;

    @Column(length = 1000)
    private String message;

    /** Ties this row to its log lines and AI calls. */
    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    /**
     * Set once the user saves this row into the catalogue.
     *
     * <p>Makes the save idempotent from the screen's point of view: a second
     * click shows the product it already created instead of making another.</p>
     */
    @Column(name = "saved_product_id")
    private Long savedProductId;

    @Column(name = "searched_at")
    private Instant searchedAt;
}
