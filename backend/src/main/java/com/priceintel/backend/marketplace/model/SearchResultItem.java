package com.priceintel.backend.marketplace.model;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A single listing returned by a marketplace search. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultItem {
    private String marketplaceItemId;
    private String title;
    private String url;
    private BigDecimal price;
    private String currency;
    private String seller;
    private String condition;

    /** Shipping cost for this listing (null = unknown; 0 = free). */
    private BigDecimal shipping;

    /** Stock status label, e.g. IN_STOCK / LOW_STOCK / OUT_OF_STOCK (null = unknown). */
    private String availability;

    /** Seller/product rating out of 5 (null = unknown). */
    private Double rating;

    /**
     * Which marketplace returned this item.
     *
     * <p>Stamped by the planner once results from several channels are merged
     * into one list — without it an Amazon ASIN and an eBay item id sit side by
     * side with no way to tell which is which.</p>
     */
    private String marketplace;

    /**
     * The country storefront this result came from — {@code US}, {@code CA},
     * {@code GB}.
     *
     * <p>Stamped by the adapter that made the call, because only it knows which
     * shop answered: a region-less Amazon lookup hunts across storefronts and
     * keeps the first with a price. Carried through to the screen so that saving
     * a result stores the listing the user actually saw.</p>
     */
    private String storefront;

    /**
     * True when {@link #price} is an estimate rather than an observed price.
     *
     * <p>Amazon prices by the visitor's location, so a UK page read from India
     * is quoted in rupees, with import duty and international shipping inside
     * the figure. Converting that to pounds gives a number worth showing and
     * worth labelling: it is not what a British buyer pays.</p>
     *
     * <p>Anything storing or comparing prices must keep this with the number.
     * An estimate that loses its label is indistinguishable from an observed
     * price afterwards, and would set a median as if it were one.</p>
     */
    private boolean priceEstimated;

    /** Why the price is an estimate, in plain English. Null when it is not one. */
    private String priceNote;

    /**
     * The product codes the marketplace states — ASIN, UPC, EAN, GTIN.
     *
     * <p>Already requested on every catalogue call, so this costs no extra call
     * and no quota. Empty when the marketplace publishes none.</p>
     */
    private java.util.List<ItemIdentifier> identifiers;
}
