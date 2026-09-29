package com.priceintel.backend.marketplace.ebay;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.priceintel.backend.dto.request.Destination;
import com.priceintel.backend.exception.MarketplaceApiException;
import com.priceintel.backend.marketplace.amazon.SimpleRateLimiter;

import lombok.extern.slf4j.Slf4j;

/**
 * Low-level eBay Browse API client. Handles auth header + marketplace header,
 * rate limiting, retry with exponential backoff, timeouts, and logging.
 */
@Slf4j
@Component
public class EbayApiClient {

    private final EbayApiProperties props;
    private final EbayOAuthTokenService tokenService;
    private final RestClient browseRestClient;
    private final SimpleRateLimiter rateLimiter;

    private static final String MARKETPLACE_HEADER = "X-EBAY-C-MARKETPLACE-ID";

    /** Carries the buyer's location, which is what makes eBay quote delivery. */
    private static final String BUYER_CONTEXT_HEADER = "X-EBAY-C-ENDUSERCTX";
    private static final long BASE_BACKOFF_MS = 500;

    public EbayApiClient(EbayApiProperties props,
                         EbayOAuthTokenService tokenService,
                         @Qualifier("ebayBrowseRestClient") RestClient browseRestClient,
                         @Qualifier("ebayRateLimiter") SimpleRateLimiter rateLimiter) {
        this.props = props;
        this.tokenService = tokenService;
        this.browseRestClient = browseRestClient;
        this.rateLimiter = rateLimiter;
    }

    /** Browse item_summary search by keyword. Returns raw JSON. */
    public String searchItems(String query, int limit) {
        return searchItems(query, limit, null);
    }

    /** As above, priced for delivery to {@code destination} when one is given. */
    public String searchItems(String query, int limit, Destination destination) {
        return searchItems(query, limit, destination, null);
    }

    /** As above, on a specific eBay site (from {@link #marketplaceIdFor}). */
    public String searchItems(String query, int limit, Destination destination,
                              String marketplaceId) {
        String path = "/buy/browse/v1/item_summary/search"
                + "?q=" + encode(query)
                + "&limit=" + Math.min(Math.max(limit, 1), 50);
        return execute("SEARCH", () -> get(path, destination, marketplaceId));
    }

    /**
     * Browse item_summary search restricted to a product barcode.
     *
     * <p>eBay resolves the code against its catalogue instead of matching it as
     * title text, so a UPC returns the product rather than the listings that
     * happen to print the number in their description.</p>
     *
     * @param type {@code UPC}, {@code EAN}, {@code GTIN} or {@code ISBN} — all
     *             carried in eBay's single {@code gtin} parameter
     */
    public String searchByIdentifier(String identifier, String type, int limit) {
        return searchByIdentifier(identifier, type, limit, null);
    }

    /**
     * As above, priced for delivery to {@code destination} when one is given.
     *
     * <p>The barcode goes in the {@code gtin} <em>query parameter</em>. It was
     * previously sent as {@code filter=upc:…}, which eBay has no such filter for
     * — so the request carried no search term at all and every identifier search
     * was rejected with HTTP 400 and errorId 12001: "the call must have a valid
     * q, category_ids, charity_ids, epid or gtin query parameter". eBay names
     * the missing parameter in its own error; that is the one to send.</p>
     *
     * <p>One parameter covers UPC, EAN, GTIN and ISBN because a GTIN is the
     * superset — a 12-digit UPC is a GTIN-12, a 13-digit EAN a GTIN-13. eBay
     * resolves whichever it is against its catalogue.</p>
     */
    public String searchByIdentifier(String identifier, String type, int limit,
                                     Destination destination) {
        return searchByIdentifier(identifier, type, limit, destination, null);
    }

    /** As above, on a specific eBay site (from {@link #marketplaceIdFor}). */
    public String searchByIdentifier(String identifier, String type, int limit,
                                     Destination destination, String marketplaceId) {
        String path = "/buy/browse/v1/item_summary/search"
                + "?gtin=" + encode(identifier.trim())
                + "&limit=" + Math.min(Math.max(limit, 1), 50);
        return execute("SEARCH_IDENTIFIER", () -> get(path, destination, marketplaceId));
    }

    /**
     * Every fixed-price listing of one product on one eBay site.
     *
     * <p>Auctions are excluded. An auction's price is the current bid, which
     * can be a fraction of what the item finally sells for — ranking it as the
     * cheapest competitor would state a price nobody can buy at.</p>
     *
     * @param codeType {@code GTIN} (a UPC or EAN) or {@code EPID} (eBay's own
     *                 catalogue product id)
     */
    public String searchProductListings(String codeType, String code, int limit,
                                        Destination destination, String marketplaceId) {
        String param = "EPID".equalsIgnoreCase(codeType) ? "epid" : "gtin";
        String path = "/buy/browse/v1/item_summary/search"
                + "?" + param + "=" + encode(code.trim())
                + "&limit=" + Math.min(Math.max(limit, 1), 50)
                + "&filter=" + encode("buyingOptions:{FIXED_PRICE}");
        return execute("OTHER_SELLERS", () -> get(path, destination, marketplaceId));
    }

    /** Browse get single item (full listing, shipping, seller, availability). */
    public String getItem(String itemId) {
        return getItem(itemId, null);
    }

    /**
     * As above, with delivery quoted to {@code destination} when one is given.
     *
     * <p>eBay exposes two id formats and a different endpoint for each. The
     * RESTful id looks like {@code v1|167815729577|0}; the number a person
     * copies out of a listing URL or the eBay app is the <em>legacy</em> id and
     * is rejected by the RESTful endpoint. Both are accepted here because a user
     * pasting an item number has no reason to know which kind they hold.</p>
     */
    public String getItem(String itemId, Destination destination) {
        return getItem(itemId, destination, null);
    }

    /**
     * The item as seen on one eBay site.
     *
     * <p>An eBay item id is global, but its price is not: the same listing is
     * quoted in dollars on EBAY_US and in Canadian dollars on EBAY_CA. Fetching
     * without the site returned the default site's currency whichever site the
     * listing was found on.</p>
     *
     * @param marketplaceId an eBay site id such as {@code EBAY_CA} (from
     *                      {@link #marketplaceIdFor}); null for the default site
     */
    public String getItem(String itemId, Destination destination, String marketplaceId) {
        String id = itemId == null ? "" : itemId.trim();
        String path = isLegacyId(id)
                ? "/buy/browse/v1/item/get_item_by_legacy_id?legacy_item_id=" + encode(id)
                : "/buy/browse/v1/item/" + encode(id);
        return execute("LISTING", () -> get(path, destination, marketplaceId));
    }

    /** A bare number is eBay's legacy item id; the RESTful one carries pipes. */
    private boolean isLegacyId(String id) {
        return id.matches("\\d{9,15}");
    }

    private String get(String path) {
        return get(path, null);
    }

    /**
     * eBay's marketplace id for a two-letter region, or null when eBay has no
     * site there.
     *
     * <p>eBay runs separate sites with separate inventory and currency, selected
     * by a header rather than a hostname. Without this the id is one global
     * setting, so choosing "United Kingdom" on screen returned US listings.</p>
     */
    public static String marketplaceIdFor(String region) {
        if (region == null || region.isBlank()) {
            return null;
        }
        return switch (region.trim().toUpperCase()) {
            case "US" -> "EBAY_US";
            case "GB", "UK" -> "EBAY_GB";
            case "CA" -> "EBAY_CA";
            case "DE" -> "EBAY_DE";
            case "FR" -> "EBAY_FR";
            case "IT" -> "EBAY_IT";
            case "ES" -> "EBAY_ES";
            case "AU" -> "EBAY_AU";
            case "IN" -> "EBAY_IN";
            // Mexico and Japan have no Browse-API site; null means "eBay does
            // not sell there", which the adapter reports rather than guessing.
            default -> null;
        };
    }

    private String get(String path, Destination destination) {
        return get(path, destination, null);
    }

    private String get(String path, Destination destination, String marketplaceId) {
        // Build an ABSOLUTE URL from the current environment so a runtime switch
        // between PRODUCTION and SANDBOX takes effect without a restart (the
        // client bean's fixed baseUrl would otherwise pin us to one host).
        // Pass a pre-built URI so RestClient does NOT re-encode the already
        // percent-encoded path (eBay item ids contain '|' → %7C; re-encoding
        // would turn it into %257C and eBay returns HTTP 400).
        var spec = browseRestClient.get()
                .uri(java.net.URI.create(props.getBaseUrl() + path))
                .header("Authorization", "Bearer " + tokenService.getAccessToken())
                // The caller's site when it named one, else the configured
                // default. Per call, so one search can read EBAY_GB and the
                // next EBAY_US without a global setting to change.
                .header(MARKETPLACE_HEADER, marketplaceId != null
                        ? marketplaceId : props.getMarketplaceId());

        String context = buyerContext(destination);
        if (context != null) {
            // Without this eBay quotes delivery for its own default location, or
            // omits it. Given the buyer's country and postcode it returns the
            // real cost to that address — which is the only figure that makes a
            // landed price a fact rather than an assumption.
            spec = spec.header(BUYER_CONTEXT_HEADER, context);
        }
        return spec.accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);
    }

    /**
     * eBay's contextualLocation header value, or null when we have no
     * destination and must let delivery stay unknown.
     */
    private String buyerContext(Destination destination) {
        if (destination == null || destination.isEmpty()) {
            return null;
        }
        String country = destination.normalisedCountry();
        String zip = destination.normalisedPostalCode();
        if (country == null) {
            // eBay requires the country; a postcode alone is ambiguous — 10001
            // is Manhattan in the US and a Copenhagen suburb in Denmark.
            return null;
        }
        StringBuilder sb = new StringBuilder("contextualLocation=")
                .append(encode("country=" + country));
        if (zip != null) {
            sb.append(encode(",zip=" + zip));
        }
        return sb.toString();
    }

    private String execute(String operation, Supplier<String> call) {
        if (!props.isFullyConfigured()) {
            throw new MarketplaceApiException(
                    "eBay Browse API is not configured. Set ebay.browse-api.enabled=true and provide credentials.");
        }
        int attempt = 0;
        while (true) {
            attempt++;
            rateLimiter.acquire();
            long start = System.currentTimeMillis();
            try {
                String body = call.get();
                log.info("eBay {} succeeded in {}ms (attempt {})",
                        operation, System.currentTimeMillis() - start, attempt);
                return body;
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                boolean transientError = status == 429 || status >= 500;
                log.warn("eBay {} HTTP {} (attempt {}): {}", operation, status, attempt,
                        truncate(e.getResponseBodyAsString()));
                if (transientError && attempt <= props.getMaxRetries()) {
                    backoff(operation, attempt);
                    continue;
                }
                throw new MarketplaceApiException("eBay " + operation + " failed with HTTP " + status, e);
            } catch (ResourceAccessException e) {
                log.warn("eBay {} connection/timeout (attempt {}): {}", operation, attempt, e.getMessage());
                if (attempt <= props.getMaxRetries()) {
                    backoff(operation, attempt);
                    continue;
                }
                throw new MarketplaceApiException("eBay " + operation + " timed out / unreachable", e);
            }
        }
    }

    private void backoff(String operation, int attempt) {
        long delay = BASE_BACKOFF_MS * (1L << (attempt - 1));
        log.info("eBay {} retrying in {}ms", operation, delay);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 300 ? s.substring(0, 300) : s;
    }
}
