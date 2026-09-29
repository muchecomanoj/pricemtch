package com.priceintel.backend.marketplace.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.priceintel.backend.marketplace.Marketplace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A price observation for a listing. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceSnapshot {
    private Marketplace marketplace;
    private String marketplaceItemId;
    private BigDecimal itemPrice;
    private BigDecimal shipping;
    private BigDecimal landedPrice;
    private String currency;
    private LocalDateTime observedAt;
    private boolean mocked;

    /** Marketplace/region this price was observed in (e.g. GB). */
    private String countryCode;

    /** The provider's own marketplace id for that region (e.g. A1F83G8C2ARO7P). */
    private String marketplaceId;
}
