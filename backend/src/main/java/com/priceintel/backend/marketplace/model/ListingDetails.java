package com.priceintel.backend.marketplace.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.priceintel.backend.marketplace.Marketplace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Detailed view of a single marketplace listing. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListingDetails {
    private Marketplace marketplace;
    private String marketplaceItemId;
    private String title;
    private String brand;
    private String url;
    private String seller;
    private String condition;
    private boolean available;
    private BigDecimal price;
    private String currency;
    private Double rating;
    private Integer reviewCount;

    /**
     * The product codes the marketplace states — ASIN, UPC, EAN, GTIN.
     *
     * <p>Requested on every catalogue call already ({@code includedData=…,
     * identifiers,…}), so surfacing them costs no extra call and no quota. Empty
     * when the marketplace publishes none.</p>
     */
    private java.util.List<ItemIdentifier> identifiers;
    private LocalDateTime sourceTimestamp;
    private boolean mocked;

    /** Marketplace/region this listing was priced in (e.g. GB). */
    private String countryCode;

    /** The provider's own marketplace id for that region (e.g. A1F83G8C2ARO7P). */
    private String marketplaceId;
}
