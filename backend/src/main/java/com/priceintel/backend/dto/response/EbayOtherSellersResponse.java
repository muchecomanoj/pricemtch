package com.priceintel.backend.dto.response;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Other eBay sellers offering the same product as one listing.
 *
 * <p>eBay has no Buy Box and no shared product page. Every seller runs a
 * separate listing, so the competitors of an eBay listing are the other
 * listings of the same product, found by the product code they share. That is
 * the whole of what this describes.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EbayOtherSellersResponse {

    /** The listing the comparison was run for. */
    private String sourceItemId;

    /** The eBay site searched — {@code US}, {@code CA}, {@code GB}. */
    private String storefront;

    /** The currency every price below is in. */
    private String currency;

    /**
     * How the other listings were found: {@code GTIN} (the listing's UPC or EAN)
     * or {@code EPID} (eBay's catalogue product id). Null when the listing
     * states neither and no search was possible.
     */
    private String matchedBy;

    /** The code that was searched for. */
    private String matchedValue;

    /** Fixed-price listings the search returned, before collapsing to one row per seller. */
    private int listingsFound;

    /** One row per seller, cheapest landed price first. */
    private List<Seller> sellers;

    /**
     * The lowest landed price in the same condition as the source listing, or
     * null when no seller in that condition quoted shipping.
     */
    private BigDecimal cheapestLandedPrice;

    /**
     * Rows left out of "cheapest" because the seller did not quote shipping to
     * the given destination. Their item price is known; what a buyer pays is not.
     */
    private int shippingUnknown;

    /** Plain English, ready to show, when something limits the result. Null otherwise. */
    private String note;

    /** One seller's listing of the product. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Seller {

        /** eBay's RESTful item id, e.g. {@code v1|167815729577|0}. */
        private String itemId;

        /** The number shown on the listing page, e.g. {@code 167815729577}. */
        private String legacyItemId;

        private String title;
        private String url;

        /** The seller's eBay username — always public on eBay, unlike Amazon. */
        private String seller;

        /**
         * Positive feedback as a percentage, e.g. {@code 99.8}.
         *
         * <p>Worth showing prominently. A seller at 99.8% and one at 60% are not
         * equal competitors, whatever their prices.</p>
         *
         * <p>Null when the seller has no feedback yet ({@code feedbackScore} 0).
         * Show that as "New seller", not 0%.</p>
         */
        private Double feedbackPercent;

        /** Total feedback received — how established the seller is. */
        private Integer feedbackScore;

        /** As eBay states it: New, Used, Certified - Refurbished, For parts or not working… */
        private String condition;

        private BigDecimal price;

        /** Null when the seller did not quote delivery to the destination. */
        private BigDecimal shipping;

        /**
         * Price plus shipping — what a buyer pays. Null when shipping is unknown:
         * the item price alone would understate the total by an unknown amount.
         */
        private BigDecimal landedPrice;

        private String currency;

        private boolean shippingKnown;

        /**
         * The lowest landed price <em>within this row's condition</em>.
         *
         * <p>Per condition rather than overall. A used unit at $40 does not
         * undercut a new one at $60 — it is a different thing for sale — and a
         * single "cheapest" badge on the used row would tell a seller of new stock
         * they are overpriced when they are not.</p>
         */
        private boolean cheapestInCondition;

        /** True for the listing the comparison was run for. */
        private boolean thisListing;

        /**
         * How many listings of this product the seller has <em>in this row's
         * condition</em>. The row shows the cheapest of them. The same seller in a
         * different condition is a separate row.
         */
        private int listingsFromSeller;
    }
}
