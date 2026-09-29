package com.priceintel.backend.entity;

import java.math.BigDecimal;
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
 * An immutable price observation for a competitor listing (FR-PRICE-003).
 * Powers the price-history chart and market statistics.
 */
@Entity
@Table(name = "listing_price_snapshots",
        indexes = {
                @Index(name = "idx_price_snapshot_listing", columnList = "listing_id, observed_at"),
                // History is looked up by the real-world listing, so every tenant
                // tracking the same item reads (and thickens) one shared series.
                @Index(name = "idx_price_snapshot_item",
                        columnList = "marketplace, storefront, marketplace_item_id, observed_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListingPriceSnapshot extends BaseEntity {

    /**
     * The competitor row this was observed for, or null when the price was seen
     * on a marketplace search that belonged to no product. The series is read by
     * (marketplace, item), so history joins up either way.
     */
    @Column(name = "listing_id")
    private Long listingId;

    /**
     * The real-world listing this observation belongs to, independent of which
     * tenant's row triggered the fetch. Two clients tracking the same ASIN share
     * one price series instead of each building a thin one of their own.
     */
    @Column(length = 20)
    private String marketplace;

    /** The country storefront this price was read in. Part of the series key. */
    @Column(nullable = false, length = 2)
    private String storefront;

    @Column(name = "marketplace_item_id", length = 100)
    private String marketplaceItemId;

    @Column(name = "item_price", precision = 12, scale = 2)
    private BigDecimal itemPrice;

    @Column(precision = 12, scale = 2)
    private BigDecimal shipping;

    @Column(name = "landed_price", precision = 12, scale = 2)
    private BigDecimal landedPrice;

    @Column(length = 10)
    private String currency;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;
}
