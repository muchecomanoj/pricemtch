package com.priceintel.backend.service.impl;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.priceintel.backend.dto.request.FeeEstimateRequest;
import com.priceintel.backend.dto.request.MarketplaceSearchRequest;
import com.priceintel.backend.marketplace.Marketplace;
import com.priceintel.backend.marketplace.MarketplaceAdapterFactory;
import com.priceintel.backend.marketplace.model.ConnectorHealth;
import com.priceintel.backend.marketplace.model.FeeEstimate;
import com.priceintel.backend.marketplace.model.ListingDetails;
import com.priceintel.backend.marketplace.model.OfferListing;
import com.priceintel.backend.marketplace.model.PriceSnapshot;
import com.priceintel.backend.marketplace.model.SalesMetrics;
import com.priceintel.backend.marketplace.model.SearchResult;
import com.priceintel.backend.marketplace.model.SearchResultItem;
import com.priceintel.backend.service.MarketplaceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketplaceServiceImpl implements MarketplaceService {

    private final MarketplaceAdapterFactory adapterFactory;

    @Override
    public Set<Marketplace> getSupportedMarketplaces() {
        return adapterFactory.getSupportedMarketplaces();
    }

    @Override
    public List<ConnectorHealth> healthAll() {
        return adapterFactory.getSupportedMarketplaces().stream()
                .map(m -> adapterFactory.getAdapter(m).health())
                .toList();
    }

    @Override
    public ConnectorHealth health(Marketplace marketplace) {
        return adapterFactory.getAdapter(marketplace).health();
    }

    /**
     * Runs the search and, unless the caller opted out, drops listings that have
     * no price. Adapters that can hunt for a priced region (Amazon) have already
     * done so; this is the backstop that guarantees no priceless listing reaches
     * a caller, whichever adapter produced it.
     */
    @Override
    public SearchResult search(Marketplace marketplace, MarketplaceSearchRequest request) {
        SearchResult result = adapterFactory.getAdapter(marketplace).search(request);
        if (!request.isPriceRequired() || result == null || result.getItems() == null) {
            return result;
        }
        List<SearchResultItem> priced = result.getItems().stream()
                .filter(i -> i.getPrice() != null && i.getPrice().signum() > 0)
                .toList();
        int dropped = result.getItems().size() - priced.size();
        if (dropped > 0) {
            log.info("{} search '{}': hid {} listing(s) with no price",
                    marketplace, request.getQuery(), dropped);
            result.setItems(priced);
            result.setTotalResults(priced.size());
            if (result.getNote() == null) {
                result.setNote(dropped + " listing(s) without a price were hidden.");
            }
        }
        return result;
    }

    @Override
    public ListingDetails getListing(Marketplace marketplace, String marketplaceItemId) {
        return adapterFactory.getAdapter(marketplace).getListing(marketplaceItemId);
    }

    @Override
    public PriceSnapshot getPrice(Marketplace marketplace, String marketplaceItemId) {
        return adapterFactory.getAdapter(marketplace).getPrice(marketplaceItemId);
    }

    @Override
    public ListingDetails getListing(Marketplace marketplace, String marketplaceItemId,
                                     String storefront) {
        return adapterFactory.getAdapter(marketplace).getListing(marketplaceItemId, storefront);
    }

    @Override
    public PriceSnapshot getPrice(Marketplace marketplace, String marketplaceItemId,
                                  String storefront) {
        return adapterFactory.getAdapter(marketplace).getPrice(marketplaceItemId, storefront);
    }

    @Override
    public List<OfferListing> getOffers(Marketplace marketplace, String marketplaceItemId,
                                        String condition, String storefront) {
        return adapterFactory.getAdapter(marketplace)
                .getOffers(marketplaceItemId, condition, storefront);
    }

    @Override
    public FeeEstimate estimateFees(Marketplace marketplace, FeeEstimateRequest request) {
        return adapterFactory.getAdapter(marketplace).estimateFees(request);
    }

    @Override
    public SalesMetrics getSales(Marketplace marketplace, String marketplaceItemId) {
        return adapterFactory.getAdapter(marketplace).getSales(marketplaceItemId);
    }

    @Override
    public List<OfferListing> getOffers(Marketplace marketplace, String marketplaceItemId, String condition) {
        return adapterFactory.getAdapter(marketplace).getOffers(marketplaceItemId, condition);
    }
}
