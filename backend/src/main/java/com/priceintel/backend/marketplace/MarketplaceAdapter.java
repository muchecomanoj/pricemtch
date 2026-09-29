package com.priceintel.backend.marketplace;

import com.priceintel.backend.dto.request.FeeEstimateRequest;
import com.priceintel.backend.dto.request.MarketplaceSearchRequest;
import com.priceintel.backend.marketplace.model.ConnectorHealth;
import com.priceintel.backend.marketplace.model.FeeEstimate;
import java.util.List;

import com.priceintel.backend.marketplace.model.ListingDetails;
import com.priceintel.backend.marketplace.model.OfferListing;
import com.priceintel.backend.marketplace.model.PriceSnapshot;
import com.priceintel.backend.marketplace.model.SalesMetrics;
import com.priceintel.backend.marketplace.model.SearchResult;

/**
 * The marketplace connector contract (the Strategy interface). Each marketplace
 * provides one implementation; the rest of the app depends only on this
 * interface, never on a concrete adapter (Dependency Inversion).
 *
 * <p>All current implementations return MOCKED data — no external API is called.</p>
 */
public interface MarketplaceAdapter {

    /** Which marketplace this adapter serves (used by the factory to select it). */
    Marketplace getMarketplace();

    SearchResult search(MarketplaceSearchRequest request);

    ListingDetails getListing(String marketplaceItemId);

    PriceSnapshot getPrice(String marketplaceItemId);

    /**
     * Listing details from one named storefront, and no other.
     *
     * <p>A region is an instruction, not a hint. Answering a question about
     * amazon.ca with amazon.com's page is how a CA$140 listing was stored as
     * US$144.29. An adapter that cannot honour the storefront must fail rather
     * than quietly answer from a different one.</p>
     *
     * @param storefront a country code ({@code US}, {@code CA}, {@code GB}); null
     *                   keeps the region-less behaviour
     */
    default ListingDetails getListing(String marketplaceItemId, String storefront) {
        if (storefront == null || storefront.isBlank()) {
            return getListing(marketplaceItemId);
        }
        throw new com.priceintel.backend.exception.MarketplaceApiException(
                getMarketplace() + " cannot look up a listing in a specific storefront");
    }

    /** A price from one named storefront, and no other. See {@link #getListing(String, String)}. */
    default PriceSnapshot getPrice(String marketplaceItemId, String storefront) {
        if (storefront == null || storefront.isBlank()) {
            return getPrice(marketplaceItemId);
        }
        throw new com.priceintel.backend.exception.MarketplaceApiException(
                getMarketplace() + " cannot price a listing in a specific storefront");
    }

    FeeEstimate estimateFees(FeeEstimateRequest request);

    SalesMetrics getSales(String marketplaceItemId);

    /**
     * Every competing seller's offer on a listing. Optional capability: sources
     * that expose only a single market price return an empty list rather than
     * pretending to know the sellers behind it.
     *
     * @param condition item condition to filter by, or null for the default
     */
    default List<OfferListing> getOffers(String marketplaceItemId, String condition) {
        return List.of();
    }

    /**
     * Offers in one named storefront. A seller's offer on amazon.com is not an
     * offer on amazon.ca, so the Buy Box holder of one says nothing about the
     * other.
     */
    default List<OfferListing> getOffers(String marketplaceItemId, String condition,
                                         String storefront) {
        if (storefront == null || storefront.isBlank()) {
            return getOffers(marketplaceItemId, condition);
        }
        return List.of();
    }

    ConnectorHealth health();
}
