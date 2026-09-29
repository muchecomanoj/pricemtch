package com.priceintel.backend.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A marketplace listing we have seen and are now tracking.
 *
 * <p>Not tenant-scoped, deliberately. What an ASIN costs is a fact about the
 * market rather than about the client who happened to search for it, so every
 * client tracking the same item reads and thickens one series — the same
 * reasoning that already keys {@link ListingPriceSnapshot} by marketplace item.
 * It is therefore <em>not</em> a competitor of anyone's product; that
 * relationship lives in {@link CompetitorListing} and stays tenant-owned.</p>
 */
@Entity
@Table(name = "marketplace_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_marketplace_item",
                columnNames = {"marketplace", "storefront", "marketplace_item_id"}),
        indexes = {
                @Index(name = "idx_marketplace_item_seen", columnList = "last_seen_at"),
                @Index(name = "idx_marketplace_item_changed", columnList = "last_changed_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketplaceItem extends BaseEntity {

    @Column(nullable = false, length = 20)
    private String marketplace;

    /**
     * The country storefront — {@code US}, {@code CA}, {@code GB}.
     *
     * <p>Part of the key. One ASIN on amazon.com and on amazon.ca is two items
     * with two price histories; keyed without this they shared one series that
     * alternated between currencies and logged every alternation as a change.</p>
     */
    @Column(nullable = false, length = 2)
    private String storefront;

    @Column(name = "marketplace_item_id", nullable = false, length = 100)
    private String marketplaceItemId;

    @Column(length = 500)
    private String title;

    @Column(length = 150)
    private String brand;

    @Column(length = 1000)
    private String url;

    /**
     * What the search returned as a seller name.
     *
     * <p>On Amazon this is the <em>brand</em> — its catalogue API publishes no
     * merchant name. Use {@link #sellerId} for the actual merchant.</p>
     */
    @Column(length = 200)
    private String seller;

    /**
     * The merchant currently holding the Buy Box.
     *
     * <p>Amazon identifies sellers by id, never by name. Fetched from the offers
     * endpoint only when a price moved, because that is the only moment the
     * answer changes a decision — and it costs a second API call.</p>
     */
    @Column(name = "seller_id", length = 64)
    private String sellerId;

    @Column(length = 50)
    private String condition;

    @Column(length = 10)
    private String currency;

    @Column(name = "item_price", precision = 12, scale = 2)
    private BigDecimal itemPrice;

    @Column(precision = 12, scale = 2)
    private BigDecimal shipping;

    @Column(name = "landed_price", precision = 12, scale = 2)
    private BigDecimal landedPrice;

    @Column(length = 50)
    private String availability;

    /** The marketplace's product codes, as the JSON array the API returns. */
    @Column(columnDefinition = "TEXT")
    private String identifiers;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    /**
     * When a value last actually moved.
     *
     * <p>Distinct from {@code lastSeenAt} and not derivable from it. Without
     * this, a price that has held steady for a month looks identical to one
     * nobody has checked for a month.</p>
     */
    @Column(name = "last_changed_at")
    private Instant lastChangedAt;

    /** How many times we have looked, including the times nothing had moved. */
    @Column(name = "observation_count", nullable = false)
    @Builder.Default
    private Integer observationCount = 0;

    @Column(name = "change_count", nullable = false)
    @Builder.Default
    private Integer changeCount = 0;
}
