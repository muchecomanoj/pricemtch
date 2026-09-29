package com.priceintel.backend.marketplace.model;

import java.math.BigDecimal;

import com.priceintel.backend.marketplace.Marketplace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Estimated sales metrics for a listing (clearly flagged as an estimate). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesMetrics {
    private Marketplace marketplace;
    private String marketplaceItemId;
    private Integer estimatedUnits;
    private BigDecimal estimatedRevenue;
    private String currency;
    private String classification; // e.g. ESTIMATED
    private String note;
    private boolean mocked;
}
