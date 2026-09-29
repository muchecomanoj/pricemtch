package com.priceintel.backend.dto.response;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A marketplace listing, with the model's judgement of whether it is the same
 * product as the one being searched for.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchedListing {

    private String marketplace;

    /**
     * The country storefront this listing was found in — {@code US},
     * {@code CA}, {@code GB}.
     *
     * <p>Send it back as {@code region} when attaching this listing. Without it
     * the attach re-fetches the item from whichever storefront answers first,
     * which stored amazon.com's US$144.29 for a listing seen on amazon.ca at
     * CA$140.</p>
     */
    private String storefront;

    private String marketplaceItemId;
    private String title;
    private String url;

    /**
     * True when {@link #price} is an estimate rather than an observed price —
     * a foreign-currency quote converted, carrying import duty and shipping.
     * Show it as an estimate; never present it as the shelf price.
     */
    private boolean priceEstimated;

    /** Why the price is an estimate, in plain English. Null when it is not one. */
    private String priceNote;
    private String seller;
    private String condition;
    private BigDecimal price;
    private BigDecimal shipping;
    private String currency;

    /** Stock status the marketplace reported, e.g. IN_STOCK (null = unknown). */
    private String availability;

    /**
     * The product codes the marketplace states — ASIN, UPC, EAN, GTIN.
     *
     * <p>Carried onto the verdict card so a reviewer can check the barcode
     * against the box in front of them. A title and a price are not enough to
     * confirm a match; a barcode very nearly is.</p>
     */
    private List<com.priceintel.backend.marketplace.model.ItemIdentifier> identifiers;

    /** {@code MATCH}, {@code EQUIVALENT}, {@code NOT_MATCH} or {@code UNCERTAIN}. */
    private String decision;

    /** How likely this is the same product, 0-100. */
    private Integer score;

    /** One sentence saying why — the part a person acts on. */
    private String reason;

    /**
     * How sure the verdict is, 0.0–1.0 — distinct from {@link #score}.
     *
     * <p>Score answers "how alike are these"; confidence answers "how much
     * evidence was there to judge on". A listing with a bare title can be judged
     * a strong match on weak evidence, and a reviewer needs to know which.</p>
     */
    private Double confidence;

    /** Attributes that agreed — brand, model, capacity, colour. */
    private List<String> matchedAttributes;

    /** Attributes that disagreed, and why they matter. */
    private List<String> conflicts;

    /**
     * The same conflicts, with the values that differ (FR-MATCH-002).
     *
     * <p>{@link #conflicts} names the attribute — "condition" — which tells a
     * reviewer where to look but not what they would find. The half that decides
     * whether a listing is comparable is <em>New vs Renewed</em>, and a bare
     * attribute name cannot carry it.</p>
     *
     * <p>{@code conflicts} is kept and derived from this list, so nothing
     * rendering the old field breaks.</p>
     */
    private List<MatchConflict> conflictDetails;

    /**
     * What could not be checked because the listing does not say.
     *
     * <p>The reason a verdict should sometimes go to a person rather than be
     * acted on: "same product, but pack size unknown" is a different thing from
     * "same product".</p>
     */
    private List<String> missingEvidence;

    /** The prompt behind this verdict, so a later change can be traced. */
    private String promptVersion;

    /**
     * Whether the deterministic rules or the model produced this.
     *
     * <p>{@code RULES} when no AI provider was available, so a caller can tell a
     * judged result from a scored one rather than assuming.</p>
     */
    private String judgedBy;
}
