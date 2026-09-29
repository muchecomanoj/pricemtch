package com.priceintel.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single row-level problem found during an import (validation failure or
 * duplicate). Collectively these form the downloadable error report / import log.
 */
@Entity
@Table(name = "import_errors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportError extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_job_id", nullable = false)
    private ImportJob importJob;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(length = 100)
    private String sku;

    @Column(length = 100)
    private String field;

    @Column(name = "error_message", nullable = false, length = 500)
    private String errorMessage;

    /** The raw row content (JSON) so the user can see exactly what failed. */
    @Column(name = "raw_data", length = 4000)
    private String rawData;
}
