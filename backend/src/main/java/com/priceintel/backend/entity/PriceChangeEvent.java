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
 * A record that one field of a listing moved (FR-PRICE-003, "change events").
 *
 * <p>A snapshot says what a value <em>was</em>; this says that it
 * <em>changed</em>. The second is the question people actually ask, and it
 * cannot be read off the snapshot series reliably — 41% of the snapshots in this
 * database repeat the previous value unchanged, so comparing adjacent rows finds
 * moves that never happened and misses the gaps between real ones.</p>
 *
 * <p>One row per field, so "the price fell and it went out of stock" is two
 * facts rather than one blurred one.</p>
 */
@Entity
@Table(name = "price_change_events",
        indexes = {
                @Index(name = "idx_price_change_item",
                        columnList = "marketplace, storefront, marketplace_item_id, observed_at"),
                @Index(name = "idx_price_change_time", columnList = "observed_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceChangeEvent extends BaseEntity {

    /** The fields worth recording a movement in. */
    public static final String PRICE = "PRICE";
    public static final String SHIPPING = "SHIPPING";
    public static final String LANDED_PRICE = "LANDED_PRICE";
    public static final String AVAILABILITY = "AVAILABILITY";

    @Column(nullable = false, length = 20)
    private String marketplace;

    /**
     * The country storefront the change happened in.
     *
     * <p>A change is only a change within one shop. Before this existed, a
     * listing seen on amazon.com and then on amazon.ca was recorded as having
     * "moved" from US$144 to CA$140.</p>
     */
    @Column(nullable = false, length = 2)
    private String storefront;

    @Column(name = "marketplace_item_id", nullable = false, length = 100)
    private String marketplaceItemId;

    /**
     * The tracked listing's title, filled in when the feed is read.
     *
     * <p>Not stored: the title belongs to the item, and copying it onto every
     * change would freeze whatever it was called that day. It is here because an
     * ASIN alone means nothing to a client — a feed of "B0DGHMNQ5Z" is
     * unreadable, and looking each one up separately is a request per row.</p>
     *
     * <p>Null when the change predates the item being tracked, or when the item
     * row has no title.</p>
     */
    @jakarta.persistence.Transient
    private String title;

    @Column(nullable = false, length = 30)
    private String field;

    /**
     * Text, so one table holds both {@code 99.00 -> 94.05} and
     * {@code IN_STOCK -> OUT_OF_STOCK}. The numeric columns below carry the
     * arithmetic for money fields and stay null for the rest.
     */
    @Column(name = "old_value", length = 200)
    private String oldValue;

    @Column(name = "new_value", length = 200)
    private String newValue;

    @Column(name = "change_amount", precision = 12, scale = 2)
    private BigDecimal changeAmount;

    @Column(name = "change_pct", precision = 9, scale = 4)
    private BigDecimal changePct;

    @Column(length = 10)
    private String currency;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    /**
     * When the previous value was observed.
     *
     * <p>Turns "the price fell 5%" into "the price fell 5% since Tuesday",
     * which is the difference between a number and a fact you can act on. Also
     * exposes a gap: a change measured against a three-week-old observation is
     * weaker evidence than one measured against yesterday's.</p>
     */
    @Column(name = "previous_observed_at")
    private Instant previousObservedAt;

    /** The merchant holding the Buy Box when the new value was seen. */
    @Column(name = "seller_id", length = 64)
    private String sellerId;

    @Column(name = "previous_seller_id", length = 64)
    private String previousSellerId;

    /**
     * Whether the merchant changed at the same moment as the price.
     *
     * <p>Separates "the same seller lowered their price" from "a different
     * seller took the Buy Box at a lower price". In a price series the two look
     * identical and lead to opposite decisions: the first is a competitor
     * undercutting you, the second is two sellers trading a slot and nothing to
     * react to. Null when the merchant was not known on one side or the
     * other.</p>
     */
    @Column(name = "seller_changed")
    private Boolean sellerChanged;
}
