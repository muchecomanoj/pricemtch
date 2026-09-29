package com.priceintel.backend.entity;

import java.math.BigDecimal;
import java.time.Instant;

import com.priceintel.backend.constants.MatchStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A competitor listing found for a product on a marketplace (FR-PRICE-001).
 * Starts as a CANDIDATE from search; a reviewer confirms or rejects it.
 */
@Entity
@Table(name = "competitor_listings",
        uniqueConstraints = @UniqueConstraint(name = "uk_competitor_listing",
                columnNames = {"product_id", "marketplace", "storefront", "marketplace_item_id"}),
        indexes = @Index(name = "idx_competitor_product", columnList = "product_id, match_status"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompetitorListing extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    /**
     * The tenant that owns this row, copied from the product.
     *
     * <p>Denormalised deliberately: the Competitor Listings and Match Review
     * screens are tenant-wide and paginate over this table directly, so without
     * a column here every page would have to join products purely to filter —
     * and, historically, simply did not filter at all.</p>
     *
     * <p>Nullable only so the column could be added to a populated table;
     * every row is backfilled and every insert sets it.</p>
     */
    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(nullable = false, length = 20)
    private String marketplace;

    /**
     * The country storefront this listing was observed in — {@code US},
     * {@code CA}, {@code GB}.
     *
     * <p>Part of the listing's identity. amazon.com and amazon.ca are separate
     * shops with separate prices, and the same ASIN exists in both; without this
     * a product could never hold both, and attaching the Canadian one returned
     * the American one.</p>
     */
    @Column(nullable = false, length = 2)
    private String storefront;

    @Column(name = "marketplace_item_id", nullable = false, length = 120)
    private String marketplaceItemId;

    @Column(length = 500)
    private String title;

    // 1000, matching marketplace_items.url — see V21 and ListingUrl.
    @Column(length = 1000)
    private String url;

    @Column(length = 150)
    private String seller;

    @Column(length = 30)
    private String condition;

    @Column(length = 10)
    private String currency;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", nullable = false, length = 20)
    private MatchStatus matchStatus = MatchStatus.CANDIDATE;

    /** Latest observed landed price (cached for quick market-price stats). */
    @Column(name = "last_price", precision = 12, scale = 2)
    private BigDecimal lastPrice;

    /** Shipping cost for this listing (null = unknown; 0 = free). */
    @Column(name = "shipping", precision = 12, scale = 2)
    private BigDecimal shipping;

    /** Stock status label: IN_STOCK / LOW_STOCK / OUT_OF_STOCK (null = unknown). */
    @Column(name = "availability", length = 20)
    private String availability;

    /** Seller/product rating out of 5 (null = unknown). */
    @Column(name = "rating")
    private Double rating;

    /**
     * Stored match confidence 0-100 (FR-MATCH-002), computed at search time so
     * the review queue can rank/sort by it in SQL. Signals themselves are
     * recomputed on read (cheap) and not persisted.
     */
    @Column(name = "match_score")
    private Integer matchScore;

    @Column(name = "source_timestamp")
    private Instant sourceTimestamp;

    /**
     * When this price was genuinely retrieved from the marketplace.
     *
     * <p>Distinct from {@link #sourceTimestamp}, which moves every time the row
     * is touched. On a cache hit the original fetch time is carried over, so
     * "as of" always tells the truth about how old the price really is —
     * never how recently we copied it.</p>
     */
    @Column(name = "last_fetched_at")
    private Instant lastFetchedAt;

    /**
     * When a search last returned this listing.
     *
     * <p>The third timestamp, and the only one that answers "what did the last
     * run find". {@link #sourceTimestamp} does not: a run served entirely from
     * the shared cache leaves the product's own rows untouched, so a monitor
     * reporting five results can leave five rows with older stamps.
     * {@link #lastFetchedAt} answers a different question again — how old the
     * price is. This one moves on every run that returns the row, fetched or
     * re-used.</p>
     */
    @Column(name = "last_seen_at")
    private Instant lastSeenAt;
}
