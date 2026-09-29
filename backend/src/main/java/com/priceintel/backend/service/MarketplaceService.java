package com.priceintel.backend.service;

import java.util.List;
import java.util.Set;

import com.priceintel.backend.dto.request.FeeEstimateRequest;
import com.priceintel.backend.dto.request.MarketplaceSearchRequest;
import com.priceintel.backend.marketplace.Marketplace;
import com.priceintel.backend.marketplace.model.ConnectorHealth;
import com.priceintel.backend.marketplace.model.FeeEstimate;
import com.priceintel.backend.marketplace.model.ListingDetails;
import com.priceintel.backend.marketplace.model.OfferListing;
import com.priceintel.backend.marketplace.model.PriceSnapshot;
import com.priceintel.backend.marketplace.model.SalesMetrics;
import com.priceintel.backend.marketplace.model.SearchResult;

/**
 * Thin service over the adapter framework (Phase 6). Delegates each call to the
 * adapter selected by the factory.
 */
public interface MarketplaceService {

    Set<Marketplace> getSupportedMarketplaces();

    List<ConnectorHealth> healthAll();

    ConnectorHealth health(Marketplace marketplace);

    SearchResult search(Marketplace marketplace, MarketplaceSearchRequest request);

    ListingDetails getListing(Marketplace marketplace, String marketplaceItemId);

    PriceSnapshot getPrice(Marketplace marketplace, String marketplaceItemId);

    /**
     * Listing details from one storefront only.
     *
     * @param storefront country code ({@code US}, {@code CA}, {@code GB}); null
     *                   lets the adapter choose, which for Amazon means the first
     *                   storefront that has a price
     */
    ListingDetails getListing(Marketplace marketplace, String marketplaceItemId, String storefront);

    /** A price from one storefront only. See {@link #getListing(Marketplace, String, String)}. */
    PriceSnapshot getPrice(Marketplace marketplace, String marketplaceItemId, String storefront);

    /** Offers in one storefront only (empty when unsupported). */
    List<OfferListing> getOffers(Marketplace marketplace, String marketplaceItemId,
                                 String condition, String storefront);

    FeeEstimate estimateFees(Marketplace marketplace, FeeEstimateRequest request);

    SalesMetrics getSales(Marketplace marketplace, String marketplaceItemId);

    /** Competing sellers' offers for a listing (empty when unsupported). */
    List<OfferListing> getOffers(Marketplace marketplace, String marketplaceItemId, String condition);
}
