package com.priceintel.backend.dto.response;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Product sales view (FR-SALES). Owned-account sales are ACTUAL (only when an
 * authorised seller feed is connected); competitor figures are clearly ESTIMATED.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesResponse {
    private Long productId;
    private String currency;

    // owned account (real seller data)
    private String ownedClassification;     // ACTUAL | UNAVAILABLE
    private Integer ownedUnits;
    private BigDecimal ownedRevenue;
    private String ownedNote;

    // competitor estimate (model-derived)
    private String estimatedClassification; // ESTIMATED | UNAVAILABLE
    private Integer estimatedUnits;
    private BigDecimal estimatedRevenue;
    private int estimateSources;            // how many listings contributed
    private String estimatedNote;

    /** Provenance of each figure above, keyed by field name (FR-REPORT-001). */
    private java.util.Map<String, String> valueStatus;
}
