package com.priceintel.backend.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A multi-marketplace search run for a product (FR-SRCH-001). Records which
 * markets were searched, the outcome, and a correlation id for tracing.
 */
@Entity
@Table(name = "search_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchJob extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** Comma-separated markets searched, e.g. "AMAZON,EBAY,WEB". */
    @Column(length = 100)
    private String markets;

    @Builder.Default
    @Column(nullable = false, length = 20)
    private String status = "RUNNING";   // RUNNING | COMPLETED | FAILED

    @Column(name = "result_count")
    private int resultCount;

    @Column(name = "correlation_id", length = 60)
    private String correlationId;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(length = 1000)
    private String note;
}
