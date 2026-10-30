package com.priceintel.backend.marketplace.scraper;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.priceintel.backend.dto.request.MarketplaceSearchRequest;
import com.priceintel.backend.exception.MarketplaceApiException;
import com.priceintel.backend.marketplace.Marketplace;
import com.priceintel.backend.marketplace.model.ListingDetails;
import com.priceintel.backend.marketplace.model.OfferListing;
import com.priceintel.backend.marketplace.model.PriceSnapshot;
import com.priceintel.backend.marketplace.model.SearchResult;
import com.priceintel.backend.utils.Storefront;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ASIN lookups in marketplaces the SP-API account cannot reach.
 *
 * <p>Amazon authorises SP-API per marketplace, and this account holds the US and
 * Canada. An ASIN searched in the UK, Germany or India returns HTTP 403 from the
 * official API — not "no results", but "you may not ask". The scraper reads the
 * public product page for exactly those storefronts.</p>
 *
 * <p>Only for ASIN lookups. The service takes an ASIN and nothing else, so a
 * title or barcode search has nothing to send it, and SP-API keeps those.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AmazonScraperSearch {

    private final AmazonScraperProperties props;
    private final AmazonScraperClient client;
    private final AmazonScraperNormalizer normalizer;

    /**
     * Whether this request belongs to the scraper rather than to SP-API.
     *
     * <p>All four conditions matter: the scraper is configured, the caller named
     * a storefront, that storefront is outside the SP-API authorisation, and the
     * query is an ASIN. A region-less search stays with SP-API, which hunts its
     * own authorised marketplaces and does it better.</p>
     */
    public boolean handles(MarketplaceSearchRequest request) {
        if (!props.isFullyConfigured() || request == null) {
            return false;
        }
        if (!"ASIN".equalsIgnoreCase(request.normalisedIdentifierType())) {
            return false;
        }
        String country = Storefront.normalise(request.getRegion());
        return country != null
                && !props.isSpApiRegion(country)
                && AmazonScraperClient.domainFor(country) != null;
    }

    /**
     * Whether the listing screen's reads for this storefront belong here.
     *
     * <p>The same test as {@link #handles}, minus the request: the listing
     * screen always has an ASIN, so only the storefront decides. Search had this
     * fallback from the start and the listing screen did not, which is why a UK
     * search found competitors and then opening one said the marketplace was not
     * connected.</p>
     */
    public boolean handlesStorefront(String storefront) {
        if (!props.isFullyConfigured()) {
            return false;
        }
        String country = Storefront.normalise(storefront);
        return country != null
                && !props.isSpApiRegion(country)
                && AmazonScraperClient.domainFor(country) != null;
    }

    /** Listing details for a storefront SP-API cannot read. */
    public ListingDetails listing(String asin, String storefront) {
        String country = Storefront.normalise(storefront);
        log.info("SP-API is not authorised for Amazon {} — reading listing {} from the page reader",
                country, asin);
        return normalizer.toListingDetails(product(asin, country), asin, country,
                props.isAcceptConvertedPrices());
    }

    /** The current price for a storefront SP-API cannot read. */
    public PriceSnapshot price(String asin, String storefront) {
        String country = Storefront.normalise(storefront);
        log.info("SP-API is not authorised for Amazon {} — reading price for {} from the page reader",
                country, asin);
        return normalizer.toPriceSnapshot(product(asin, country), asin, country,
                props.isAcceptConvertedPrices());
    }

    /** The buy-box offer, which is all the public page names. */
    public List<OfferListing> offers(String asin, String storefront) {
        String country = Storefront.normalise(storefront);
        return normalizer.toOffers(product(asin, country), asin, country,
                props.isAcceptConvertedPrices());
    }

    /**
     * Fetches the page, turning a reader failure into something the screen can
     * show. Thrown rather than returned empty: unlike a search across channels,
     * these calls have nothing else to fall back on, and a blank page with no
     * reason is worse than a sentence saying the reader could not be reached.
     */
    private String product(String asin, String country) {
        try {
            return client.getProduct(asin, AmazonScraperClient.domainFor(country));
        } catch (MarketplaceApiException e) {
            throw new MarketplaceApiException("Amazon " + country + " is outside the SP-API "
                    + "authorisation, and the page reader could not be reached: " + e.getMessage());
        }
    }

    /**
     * The listing, or an empty result explaining why there is none.
     *
     * <p>A failure here is reported as an empty result rather than thrown: the
     * scraper is a fallback for marketplaces that would otherwise answer nothing
     * at all, and a search of three channels should not fail because the
     * unofficial one was busy.</p>
     */
    public SearchResult search(MarketplaceSearchRequest request) {
        String country = Storefront.normalise(request.getRegion());
        String domain = AmazonScraperClient.domainFor(country);
        String asin = request.getQuery() == null ? "" : request.getQuery().trim().toUpperCase();
        log.info("SP-API is not authorised for Amazon {} — reading {} from the scraper instead",
                country, asin);
        try {
            String json = client.getProduct(asin, domain);
            SearchResult result = normalizer.toSearchResult(
                    json, asin, country, props.isAcceptConvertedPrices());
            result.setRegionsTried(new ArrayList<>(java.util.List.of(country + " (scraper)")));
            return result;
        } catch (MarketplaceApiException e) {
            log.warn("Amazon scraper failed for {} in {}: {}", asin, country, e.getMessage());
            return SearchResult.builder()
                    .marketplace(Marketplace.AMAZON)
                    .query(asin)
                    .items(new ArrayList<>())
                    .totalResults(0)
                    .sourceTimestamp(java.time.LocalDateTime.now())
                    .regionsUnavailable(new ArrayList<>(java.util.List.of(
                            country + " (scraper unavailable)")))
                    .note("Amazon " + country + " is not covered by the SP-API authorisation, and "
                            + "the page reader could not be reached: " + e.getMessage())
                    .build();
        }
    }
}
