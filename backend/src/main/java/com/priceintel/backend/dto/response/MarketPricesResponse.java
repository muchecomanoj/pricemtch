package com.priceintel.backend.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Aggregate competitor price statistics for a product (FR-PRICE-004). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketPricesResponse {
    private Long productId;
    private String currency;
    private int competitorCount;      // listings included in the stats
    private BigDecimal lowest;
    private BigDecimal highest;
    private BigDecimal average;
    private BigDecimal median;
    /** Only listings with MATCHED/EQUIVALENT status are counted; CANDIDATE excluded by default. */
    private boolean matchedOnly;

    /** Our own selling price, so the rank below can be read without a second call. */
    private BigDecimal ourPrice;

    /**
     * Where our price sits among all offers, cheapest first — 1 means nobody
     * undercuts us.
     *
     * <p>Null when the product has no price of its own, or when no competitor
     * has one: a rank against nothing would read as a strong position rather
     * than an absent one.</p>
     */
    private Integer marketRank;

    /** How many offers the rank is out of, ours included. */
    private Integer marketRankTotal;

    /**
     * How much the rank can be trusted:
     * <ul>
     *   <li>{@code CONFIRMED} — every offer counted is a reviewed match</li>
     *   <li>{@code MIXED} — some are still unreviewed candidates</li>
     *   <li>{@code UNREVIEWED} — none has been confirmed yet</li>
     * </ul>
     *
     * <p>A rank computed against candidates nobody has checked can be wrong in
     * either direction — a spare part priced at $21 makes us look expensive,
     * and a bundle priced at $300 makes us look cheap.</p>
     */
    private String marketRankBasis;

    /** How far our price is from the cheapest offer. Negative means we are the cheapest. */
    private BigDecimal priceGapToLowest;

    /**
     * Provenance of each figure above, keyed by field name — {@code lowest},
     * {@code highest}, {@code average}, {@code median} (FR-REPORT-001).
     *
     * <p>Values are {@code ACTUAL}, {@code CALCULATED}, {@code ESTIMATED},
     * {@code STALE} or {@code UNAVAILABLE}. These statistics are arithmetic over
     * observed prices, so they read CALCULATED while their inputs are fresh and
     * STALE once the newest observation passes the freshness threshold.</p>
     */
    private Map<String, String> valueStatus;

    /** When the newest price behind these statistics was observed. */
    private Instant observedAt;

    /**
     * How many listings were left out because their currency could not be
     * converted into {@link #currency}.
     *
     * <p>Non-zero means these statistics describe part of the market. Saying so
     * is the point: the alternative was averaging C$95 with $70 and labelling
     * the result in dollars, which reads as a fact and is not one.</p>
     */
    private int excludedForCurrency;

    /**
     * The currencies of the excluded listings, so the fix is obvious — those are
     * exactly the rates that need entering.
     */
    private java.util.List<String> missingRatesFor;

    /**
     * True when at least one figure above was converted from another currency.
     *
     * <p>A converted price is only as good as the rate behind it, and a stored
     * rate can be old. The screen should be able to say so.</p>
     */
    private boolean converted;
}
