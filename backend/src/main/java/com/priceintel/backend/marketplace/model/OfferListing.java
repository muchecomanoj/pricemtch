package com.priceintel.backend.marketplace.model;

import java.math.BigDecimal;

import com.priceintel.backend.marketplace.Marketplace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One competing seller's offer on a listing.
 *
 * <p>Distinct from {@link PriceSnapshot}, which records a single market price
 * for an item. This is the seller-level view: who is selling it, at what price,
 * and how they fulfil it — the data needed to answer "who is undercutting us"
 * rather than just "what does the market charge".</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfferListing {

    private Marketplace marketplace;
    private String marketplaceItemId;

    /** The marketplace's seller id. Null when the source does not expose it. */
    private String sellerId;

    /** Item condition as reported by the seller (New, Used, Refurbished...). */
    private String condition;

    /** The seller's asking price, excluding shipping. */
    private BigDecimal listingPrice;

    private BigDecimal shipping;

    /** Price + shipping — the figure a buyer actually compares. */
    private BigDecimal landedPrice;

    private String currency;

    /** True when this offer currently holds the featured (Buy Box) position. */
    private boolean buyBoxWinner;

    /** True when the marketplace fulfils the order on the seller's behalf (FBA). */
    private boolean fulfilledByMarketplace;

    /** Seller's positive feedback percentage (0-100), null if unknown. */
    private Double sellerPositiveFeedbackPercent;

    /** Number of feedback ratings the seller has, null if unknown. */
    private Integer sellerFeedbackCount;

    /** Marketplace/region this offer was read from (e.g. US). */
    private String countryCode;
}
