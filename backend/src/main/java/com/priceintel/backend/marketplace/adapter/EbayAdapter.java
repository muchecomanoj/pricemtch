package com.priceintel.backend.marketplace.adapter;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.priceintel.backend.dto.request.FeeEstimateRequest;
import com.priceintel.backend.dto.request.MarketplaceSearchRequest;
import com.priceintel.backend.exception.MarketplaceApiException;
import com.priceintel.backend.marketplace.Marketplace;
import com.priceintel.backend.marketplace.MarketplaceAdapter;
import com.priceintel.backend.marketplace.ebay.EbayApiClient;
import com.priceintel.backend.marketplace.ebay.EbayApiProperties;
import com.priceintel.backend.marketplace.ebay.EbayOAuthTokenService;
import com.priceintel.backend.marketplace.ebay.EbayResponseNormalizer;
import com.priceintel.backend.marketplace.model.ConnectorHealth;
import com.priceintel.backend.marketplace.model.FeeEstimate;
import com.priceintel.backend.marketplace.model.ListingDetails;
import com.priceintel.backend.marketplace.model.PriceSnapshot;
import com.priceintel.backend.marketplace.model.SalesMetrics;
import com.priceintel.backend.marketplace.model.SearchResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REAL eBay adapter backed by the Browse API. Replaces the dummy adapter.
 * Fails cleanly with a MarketplaceApiException (502) when not configured.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EbayAdapter implements MarketplaceAdapter {

    private final EbayApiClient client;
    private final EbayResponseNormalizer normalizer;
    private final EbayApiProperties props;
    private final EbayOAuthTokenService tokenService;

    /** Barcode types eBay's Browse filters can resolve directly. */
    private static final java.util.Set<String> EBAY_IDENTIFIER_TYPES =
            java.util.Set.of("UPC", "EAN", "GTIN", "ISBN");

    @Override
    public Marketplace getMarketplace() {
        return Marketplace.EBAY;
    }

    @Override
    public SearchResult search(MarketplaceSearchRequest request) {
        int limit = request.getMaxResults() != null ? request.getMaxResults() : 10;
        String type = request.normalisedIdentifierType();
        String json;
        // Delivery is quoted to the buyer's location when we know it. eBay
        // sellers ship from everywhere, so shipping is a far larger share of
        // landed price here than on Amazon — a destination changes the ranking.
        var destination = request.getDestination();

        // The site the caller asked for. eBay runs separate marketplaces with
        // separate inventory and currency, so "United Kingdom" must read
        // EBAY_GB rather than returning US listings from the default site.
        String site = null;
        if (request.getRegion() != null && !request.getRegion().isBlank()) {
            site = EbayApiClient.marketplaceIdFor(request.getRegion());
            if (site == null) {
                // eBay has no Browse site there. Saying so beats quietly
                // answering with a different country's listings.
                log.info("eBay has no marketplace for region {} — no results",
                        request.getRegion());
                return SearchResult.builder()
                        .marketplace(Marketplace.EBAY)
                        .query(request.getQuery())
                        .items(new java.util.ArrayList<>()).totalResults(0)
                        .sourceTimestamp(java.time.LocalDateTime.now())
                        .note("eBay has no marketplace for " + request.getRegion() + ".")
                        .build();
            }
        }

        if (type != null && EBAY_IDENTIFIER_TYPES.contains(type)) {
            json = client.searchByIdentifier(request.getQuery(), type, limit, destination, site);
        } else {
            // ASIN and MPN have no eBay equivalent, so they run as keywords. An
            // ASIN will find nothing, which is the honest answer: eBay does not
            // index Amazon's identifiers.
            json = client.searchItems(request.getQuery(), limit, destination, site);
        }
        SearchResult result = normalizer.toSearchResult(json, request.getQuery());
        // Every result carries the site that answered, so saving one stores the
        // listing as quoted there — not re-quoted later from the default site.
        String storefront = com.priceintel.backend.utils.Storefront.normalise(
                site != null ? site : props.getMarketplaceId());
        result.getItems().forEach(i -> i.setStorefront(storefront));

        // A successful call that yields nothing is ambiguous — eBay may genuinely
        // have no matches, or we may be pointed at the sandbox (which is nearly
        // empty), or the payload shape may have moved. Say which, because "no
        // results" on a marketplace with thousands of listings is a real signal.
        if (result.getItems().isEmpty()) {
            log.warn("eBay search '{}' returned NO items — env={} host={} marketplace={}. Raw: {}",
                    request.getQuery(), props.getEnvironment(), props.getBaseUrl(),
                    props.getMarketplaceId(), truncate(json));
        } else {
            log.info("eBay search '{}' returned {} item(s) of {} reported [{} · {}]",
                    request.getQuery(), result.getItems().size(), result.getTotalResults(),
                    props.getEnvironment(), props.getMarketplaceId());
        }
        return result;
    }

    private String truncate(String s) {
        if (s == null) {
            return "<null>";
        }
        return s.length() > 400 ? s.substring(0, 400) + "…" : s;
    }

    @Override
    public ListingDetails getListing(String itemId) {
        return getListing(itemId, null);
    }

    @Override
    public PriceSnapshot getPrice(String itemId) {
        return getPrice(itemId, null);
    }

    /**
     * The listing as quoted on one eBay site.
     *
     * <p>The item id is global but the price is per site, so a listing found on
     * EBAY_CA has to be fetched from EBAY_CA or it comes back in US dollars.</p>
     */
    @Override
    public ListingDetails getListing(String itemId, String storefront) {
        String site = siteFor(storefront);
        ListingDetails details = normalizer.toListingDetails(client.getItem(itemId, null, site));
        details.setCountryCode(com.priceintel.backend.utils.Storefront.normalise(site));
        details.setMarketplaceId(site);
        return details;
    }

    @Override
    public PriceSnapshot getPrice(String itemId, String storefront) {
        String site = siteFor(storefront);
        PriceSnapshot snapshot = normalizer.toPriceSnapshot(client.getItem(itemId, null, site));
        snapshot.setCountryCode(com.priceintel.backend.utils.Storefront.normalise(site));
        snapshot.setMarketplaceId(site);
        return snapshot;
    }

    /**
     * The eBay site id for a storefront, or the configured default when none is
     * named. A named storefront eBay does not serve is refused rather than
     * answered from the default site.
     */
    private String siteFor(String storefront) {
        if (storefront == null || storefront.isBlank()) {
            return props.getMarketplaceId();
        }
        String site = EbayApiClient.marketplaceIdFor(storefront);
        if (site == null) {
            throw new MarketplaceApiException("eBay has no marketplace for region " + storefront);
        }
        return site;
    }

    @Override
    public FeeEstimate estimateFees(FeeEstimateRequest request) {
        // The Browse API does not expose seller fee estimates.
        throw new MarketplaceApiException("eBay Browse API does not provide fee estimates");
    }

    @Override
    public SalesMetrics getSales(String itemId) {
        // Sales history requires the restricted Marketplace Insights API, not Browse.
        return SalesMetrics.builder()
                .marketplace(Marketplace.EBAY)
                .marketplaceItemId(itemId)
                .classification("UNAVAILABLE")
                .note("eBay sales history requires the restricted Marketplace Insights API")
                .mocked(false)
                .build();
    }

    @Override
    public ConnectorHealth health() {
        if (!props.isFullyConfigured()) {
            return ConnectorHealth.builder()
                    .marketplace(Marketplace.EBAY).healthy(false).status("NOT_CONFIGURED")
                    .message("Set ebay.browse-api.enabled=true and provide credentials to activate.")
                    .checkedAt(LocalDateTime.now()).mocked(false).build();
        }
        try {
            tokenService.getAccessToken();
            return ConnectorHealth.builder()
                    .marketplace(Marketplace.EBAY).healthy(true).status("UP")
                    .message("Browse API reachable; OAuth token obtained")
                    .checkedAt(LocalDateTime.now()).mocked(false).build();
        } catch (Exception e) {
            return ConnectorHealth.builder()
                    .marketplace(Marketplace.EBAY).healthy(false).status("DOWN")
                    .message("eBay auth failed: " + e.getMessage())
                    .checkedAt(LocalDateTime.now()).mocked(false).build();
        }
    }
}
