package com.priceintel.backend.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetitorListingResponse {
    private Long id;
    private Long productId;
    private String productTitle;
    private String marketplace;

    /**
     * The country storefront — {@code US}, {@code CA}, {@code GB}. The same ASIN
     * can be attached once per storefront, as two separate competitors with two
     * separate price histories.
     */
    private String storefront;

    private String marketplaceItemId;
    private String title;
    private String url;
    private String seller;
    private String condition;
    private String currency;
    private String matchStatus;
    private BigDecimal lastPrice;
    private BigDecimal shipping;
    private String availability;
    private Double rating;
    private Instant sourceTimestamp;

    /**
     * When this price was actually read from the marketplace — the honest "as
     * of" for the UI. May predate {@code sourceTimestamp} when the price came
     * from a shared earlier fetch rather than a fresh call.
     */
    private Instant lastFetchedAt;

    /**
     * When a search last returned this listing — how current the <em>match</em>
     * is, as opposed to how current the price is.
     */
    private Instant lastSeenAt;

    // ---- what this listing would be worth to us ----
    //
    // A competitor row shows price, shipping and condition but nothing about
    // whether it is worth acting on. These answer that: if we sold at this
    // listing's landed price, what would we make? Computed server-side against
    // the same cost logic as /products/{id}/profitability, so the two can never
    // disagree.

    /** Net profit per unit at this listing's landed price. Null when unknown. */
    private BigDecimal estimatedMargin;

    /** Contribution margin as a percentage at that price. Null when unknown. */
    private BigDecimal estimatedMarginPct;

    /**
     * Where the cost inputs came from, so a figure is never trusted blindly:
     * <ul>
     *   <li>{@code PRODUCT_PROFILE} — the product's own costs</li>
     *   <li>{@code TENANT_DEFAULT} — the tenant-wide model, not product-specific</li>
     *   <li>{@code CURRENCY_MISMATCH} — listing and costs are in different
     *       currencies and no FX rate exists, so no figure is offered</li>
     *   <li>{@code null} — no costs configured; the UI should say "add costs"
     *       rather than show 0%</li>
     * </ul>
     */
    private String marginBasis;

    /**
     * Provenance of the numbers on this row, keyed by field name —
     * {@code lastPrice}, {@code landedPrice}, {@code estimatedMargin}
     * (FR-REPORT-001).
     *
     * <p>A price read from the marketplace is ACTUAL until it passes the
     * freshness threshold, then STALE. Landed price and margin are CALCULATED
     * from it, and inherit its staleness. A margin derived from tenant-default
     * costs rather than the product's own is ESTIMATED, because the inputs are
     * not specific to this product.</p>
     */
    private Map<String, String> valueStatus;

    /**
     * Why this listing cannot be confirmed as a comparable, or null when it can
     * (FR-MATCH-001).
     *
     * <p>Set when a hard rule fails — a different pack size or condition. An
     * exact identifier match does not clear it: a 2-pack sharing an ASIN family
     * with a single unit is still a different offer, and pricing against it
     * would be wrong. The UI should disable "accept" and show this reason.</p>
     */
    private String blockedReason;

    /** Landed price — item plus shipping — the figure a buyer actually pays. */
    private BigDecimal landedPrice;

    /**
     * Whether this listing has at least one priced observation behind it, and so
     * would draw a real curve on the price-history chart.
     *
     * <p>Most candidates never get a price: the marketplace lists the product
     * but nobody is selling it. Offering those in the history picker invites the
     * user to select a row that can only render an empty chart, which reads as a
     * broken screen rather than as missing data. The picker should show only
     * listings where this is true; the review list must keep showing them all,
     * since an unpriced candidate is still a candidate to accept or reject.</p>
     */
    private boolean hasPriceHistory;

    /**
     * The model's opinion on this candidate, when one has been formed.
     *
     * <p>Sits beside {@link #matchScore} rather than replacing it: the rules
     * score is reproducible and the model's is not, so both are shown and the
     * reviewer decides. Null means nothing has judged this pair yet.</p>
     */
    private AiVerdict aiVerdict;

    /** MATCH / NOT_MATCH / UNCERTAIN, with the model's reasoning. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiVerdict {
        private String decision;
        private Integer score;
        private String reason;
        /** Which engine said so — GROQ, OPENAI, OLLAMA. */
        private String provider;
        private String model;
        private Instant judgedAt;
    }

    /** Match confidence 0-100 (null if not scored, e.g. product missing). */
    private Integer matchScore;
    /** The signals (chips) behind the score — supports and warnings. */
    private List<MatchSignal> matchSignals;
    /** Convenience: labels of the positive signals (why it's a match). */
    private List<String> reasons;
    /** Convenience: labels of the negative signals (why it might NOT be). */
    private List<String> conflicts;
}
