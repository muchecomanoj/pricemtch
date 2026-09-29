package com.priceintel.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Stores the RAW JSON returned by an external source (e.g. Amazon SP-API),
 * exactly as received, for auditability and reprocessing. The canonical Product
 * is derived from these but kept separately.
 */
@Entity
@Table(name = "raw_source_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RawSourceRecord extends BaseEntity {

    /** e.g. "AMAZON". Kept as a plain string to avoid coupling to the adapter enum. */
    @Column(nullable = false, length = 30)
    private String source;

    /** e.g. the ASIN or marketplace item id. */
    @Column(name = "external_id", nullable = false, length = 200)
    private String externalId;

    /** Which operation produced this payload: CATALOG, PRICING, FEES, etc. */
    @Column(nullable = false, length = 30)
    private String operation;

    @Column(length = 64)
    private String checksum;

    /** The raw JSON payload as text. */
    @Column(name = "payload", columnDefinition = "text")
    private String payload;
}
