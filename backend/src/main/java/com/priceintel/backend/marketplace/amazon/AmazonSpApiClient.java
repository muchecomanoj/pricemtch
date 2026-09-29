package com.priceintel.backend.marketplace.amazon;

import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.priceintel.backend.exception.MarketplaceApiException;

import lombok.extern.slf4j.Slf4j;

/**
 * Low-level Amazon SP-API HTTP client. Handles authentication header injection,
 * rate limiting, retry with exponential backoff, timeouts (via the RestClient),
 * and logging. Returns the raw JSON string for each operation so callers can
 * both store it and parse it.
 *
 * <p>Endpoints used:
 * <ul>
 *   <li>Catalog Items 2022-04-01</li>
 *   <li>Product Pricing v0</li>
 *   <li>Product Fees v0</li>
 * </ul>
 */
@Slf4j
@Component
public class AmazonSpApiClient {

    private final AmazonSpApiProperties props;
    private final LwaTokenService tokenService;
    private final RestClient spApiRestClient;
    private final SimpleRateLimiter rateLimiter;
    private final com.priceintel.backend.service.impl.RawSourceArchiver archiver;

    private static final String ACCESS_TOKEN_HEADER = "x-amz-access-token";
    private static final long BASE_BACKOFF_MS = 500;

    public AmazonSpApiClient(AmazonSpApiProperties props,
                             LwaTokenService tokenService,
                             @Qualifier("spApiRestClient") RestClient spApiRestClient,
                             @Qualifier("amazonRateLimiter") SimpleRateLimiter rateLimiter,
                             com.priceintel.backend.service.impl.RawSourceArchiver archiver) {
        this.props = props;
        this.tokenService = tokenService;
        this.spApiRestClient = spApiRestClient;
        this.rateLimiter = rateLimiter;
        this.archiver = archiver;
    }

    /** Catalog Items: fetch canonical catalog data for an ASIN. */
    public String getCatalogItem(String asin) {
        return getCatalogItem(asin, props.getPrimaryMarketplace());
    }

    /** Catalog Items: fetch canonical catalog data for an ASIN in one marketplace. */
    public String getCatalogItem(String asin, AmazonMarketplace market) {
        String path = "/catalog/2022-04-01/items/" + asin
                + "?marketplaceIds=" + market.getMarketplaceId()
                + "&includedData=summaries,identifiers,images,attributes,productTypes";
        return execute("CATALOG", market, () -> get(market, path));
    }

    /** Catalog Items: keyword search. */
    public String searchCatalog(String keywords, int pageSize) {
        return searchCatalog(keywords, pageSize, props.getPrimaryMarketplace());
    }

    /** Catalog Items: keyword search within one marketplace. */
    public String searchCatalog(String keywords, int pageSize, AmazonMarketplace market) {
        String path = "/catalog/2022-04-01/items"
                + "?keywords=" + encode(keywords)
                + "&marketplaceIds=" + market.getMarketplaceId()
                + "&includedData=summaries,identifiers,images"
                + "&pageSize=" + Math.min(Math.max(pageSize, 1), 20);
        return execute("CATALOG_SEARCH", market, () -> get(market, path));
    }

    /**
     * Catalog Items: look an item up by a product identifier rather than by
     * keywords.
     *
     * <p>A barcode passed as keywords is matched against title text and usually
     * misses; this asks Amazon to resolve the identifier itself, which either
     * returns the exact item or nothing. "Nothing" is then a real answer — the
     * product is not in that marketplace's catalogue — rather than an artefact
     * of how the query was phrased.</p>
     *
     * @param identifiersType one of Amazon's accepted types — {@code UPC},
     *                        {@code EAN}, {@code GTIN}, {@code ISBN},
     *                        {@code JAN}. ASIN has its own endpoint
     *                        ({@link #getCatalogItem}) and is not passed here.
     */
    public String searchCatalogByIdentifier(String identifier, String identifiersType,
            AmazonMarketplace market) {
        String path = "/catalog/2022-04-01/items"
                + "?identifiers=" + encode(identifier)
                + "&identifiersType=" + encode(identifiersType)
                + "&marketplaceIds=" + market.getMarketplaceId()
                + "&includedData=summaries,identifiers,images";
        return execute("CATALOG_IDENTIFIER", market, () -> get(market, path));
    }

    /**
     * Product Pricing: the COMPETITIVE (featured/Buy-Box) price for an ASIN.
     * Unlike /price (which returns only the caller's own offers), competitivePrice
     * returns the market price of any ASIN — what we want for competitor tracking.
     */
    public String getPricing(String asin) {
        return getCompetitivePricing(java.util.List.of(asin));
    }

    /** Competitive (Buy-Box) price for an ASIN in one marketplace. */
    public String getPricing(String asin, AmazonMarketplace market) {
        return getCompetitivePricing(java.util.List.of(asin), market);
    }

    /**
     * Competitive (Buy-Box) prices for up to 20 ASINs in one call — used to
     * enrich search results with prices (Amazon catalog search omits price).
     */
    public String getCompetitivePricing(java.util.List<String> asins) {
        return getCompetitivePricing(asins, props.getPrimaryMarketplace());
    }

    /** Competitive prices for up to 20 ASINs within one marketplace. */
    public String getCompetitivePricing(java.util.List<String> asins, AmazonMarketplace market) {
        String joined = String.join(",", asins.size() > 20 ? asins.subList(0, 20) : asins);
        String path = "/products/pricing/v0/competitivePrice"
                + "?MarketplaceId=" + market.getMarketplaceId()
                + "&Asins=" + joined
                + "&ItemType=Asin";
        // Archived under the whole ASIN list: the payload covers all of them,
        // and the repository's containment lookup finds it from any one.
        return execute("PRICING", market, joined, () -> get(market, path));
    }

    /**
     * Product Pricing: the full offer list for ONE ASIN — every competing
     * seller, their price, shipping, fulfilment channel and feedback.
     *
     * <p>Complements {@link #getCompetitivePricing} rather than replacing it:
     * this is one ASIN per call (expensive against the rate limit) but it reads
     * the offers directly, so it still returns a price when nobody holds the
     * Buy Box and competitivePrice therefore comes back empty.</p>
     */
    public String getItemOffers(String asin, AmazonMarketplace market, String condition) {
        String cond = condition == null || condition.isBlank() ? "New" : condition;
        String path = "/products/pricing/v0/items/" + asin + "/offers"
                + "?MarketplaceId=" + market.getMarketplaceId()
                + "&ItemCondition=" + encode(cond);
        return execute("OFFERS", market, asin, () -> get(market, path));
    }

    /** Product Fees: estimate fees for an ASIN at a given price. */
    public String getFeesEstimate(String asin, String currency, String amount) {
        AmazonMarketplace market = props.getPrimaryMarketplace();
        String path = "/products/fees/v0/items/" + asin + "/feesEstimate";
        String body = """
                {
                  "FeesEstimateRequest": {
                    "MarketplaceId": "%s",
                    "IdType": "ASIN",
                    "IdValue": "%s",
                    "PriceToEstimateFees": {
                      "ListingPrice": { "CurrencyCode": "%s", "Amount": %s }
                    },
                    "Identifier": "fees-%s"
                  }
                }
                """.formatted(market.getMarketplaceId(), asin, currency, amount, asin);
        return execute("FEES", market, asin, () -> post(market, path, body));
    }

    // ---------- HTTP primitives ----------

    private String get(AmazonMarketplace market, String path) {
        // Absolute URL built from the target marketplace's regional host, so one
        // lookup can walk several regions without rebuilding the client bean
        // (its fixed baseUrl would otherwise pin every call to one region).
        return spApiRestClient.get()
                .uri(market.getEndpoint() + path)
                .header(ACCESS_TOKEN_HEADER, tokenService.getAccessToken())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);
    }

    private String post(AmazonMarketplace market, String path, String jsonBody) {
        return spApiRestClient.post()
                .uri(market.getEndpoint() + path)
                .header(ACCESS_TOKEN_HEADER, tokenService.getAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(jsonBody)
                .retrieve()
                .body(String.class);
    }

    // ---------- resilience wrapper ----------

    /**
     * Rate-limits, then runs the call, retrying transient failures (HTTP 429,
     * 5xx, connection/read timeouts) with exponential backoff.
     */
    private String execute(String operation, AmazonMarketplace market, Supplier<String> call) {
        return execute(operation, market, null, call);
    }

    /**
     * As above, additionally keeping the payload as evidence under
     * {@code externalId}. Archiving happens here rather than at each call site so
     * a new caller cannot forget it, and only after a successful response — a
     * retry that eventually succeeds stores one payload, not one per attempt.
     */
    private String execute(String operation, AmazonMarketplace market,
            String externalId, Supplier<String> call) {
        if (!props.isFullyConfigured()) {
            throw new MarketplaceApiException(
                    "Amazon SP-API is not configured. Set amazon.sp-api.enabled=true and provide credentials.");
        }
        int attempt = 0;
        while (true) {
            attempt++;
            rateLimiter.acquire();
            long start = System.currentTimeMillis();
            try {
                String body = call.get();
                log.info("Amazon {} [{}] succeeded in {}ms (attempt {})",
                        operation, market, System.currentTimeMillis() - start, attempt);
                if (externalId != null) {
                    archiver.archive("AMAZON", operation, externalId, body);
                }
                return body;
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                boolean transientError = status == 429 || status >= 500;
                log.warn("Amazon {} [{}] HTTP {} (attempt {}): {}", operation, market, status, attempt,
                        truncate(e.getResponseBodyAsString()));
                if (transientError && attempt <= props.getMaxRetries()) {
                    backoff(operation, attempt);
                    continue;
                }
                throw new MarketplaceApiException(
                        "Amazon " + operation + " in " + market + " failed with HTTP " + status, e);
            } catch (ResourceAccessException e) {
                // Connection refused / timeout.
                log.warn("Amazon {} [{}] connection/timeout error (attempt {}): {}",
                        operation, market, attempt, e.getMessage());
                if (attempt <= props.getMaxRetries()) {
                    backoff(operation, attempt);
                    continue;
                }
                throw new MarketplaceApiException(
                        "Amazon " + operation + " in " + market + " timed out / unreachable", e);
            }
        }
    }

    private void backoff(String operation, int attempt) {
        long delay = BASE_BACKOFF_MS * (1L << (attempt - 1)); // 500, 1000, 2000, ...
        log.info("Amazon {} retrying in {}ms", operation, delay);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private String encode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 300 ? s.substring(0, 300) : s;
    }
}
