package com.priceintel.backend.marketplace.scraper;

import java.util.ArrayList;

import org.springframework.stereotype.Component;

import com.priceintel.backend.dto.request.MarketplaceSearchRequest;
import com.priceintel.backend.exception.MarketplaceApiException;
import com.priceintel.backend.marketplace.Marketplace;
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
