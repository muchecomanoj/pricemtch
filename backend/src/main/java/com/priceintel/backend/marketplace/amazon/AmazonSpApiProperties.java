package com.priceintel.backend.marketplace.amazon;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for the Amazon Selling Partner API integration, bound from
 * properties prefixed {@code amazon.sp-api}. Credentials come from environment
 * variables; nothing is hard-coded.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "amazon.sp-api")
public class AmazonSpApiProperties {

    /** Master switch. When false the adapter is inactive and reports NOT_CONFIGURED. */
    private boolean enabled = false;

    /** NA, EU, or FE — determines the SP-API endpoint host. */
    private String region = "NA";

    private String marketplaceId;
    private String clientId;
    private String clientSecret;
    private String refreshToken;
    private String lwaTokenUrl = "https://api.amazon.com/auth/o2/token";

    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 15000;
    private int maxRetries = 3;
    private double ratePerSecond = 2;

    /**
     * When a lookup finds the product but no price in the primary marketplace,
     * retry it against the other marketplaces in {@link #getSearchMarketplaceIds()}
     * until one returns a price. Turn off to query the primary marketplace only.
     */
    private boolean regionFallbackEnabled = true;

    /**
     * Ordered fallback marketplaces, as Amazon marketplace ids or country codes
     * (e.g. {@code A1F83G8C2ARO7P,DE,JP}). The configured {@link #marketplaceId}
     * is always tried first. Empty = use the built-in order.
     */
    private List<String> searchMarketplaceIds = new ArrayList<>();

    /**
     * Hard cap on marketplaces actually <b>queried</b> per lookup, including the
     * primary. Each one costs API calls against a shared rate limit, so keep it
     * small. Marketplaces skipped because the connector is known to be
     * unauthorized for them do not consume the budget.
     */
    private int maxRegionAttempts = 4;

    /**
     * After the batched competitivePrice pass, retry still-unpriced items one at
     * a time via getItemOffers. That call reads the offer list directly, so it
     * finds a price when no seller holds the Buy Box — the main reason listings
     * come back priceless.
     */
    private boolean offerFallbackEnabled = true;

    /**
     * Cap on per-item offer lookups in a single search. Unlike competitivePrice
     * (20 ASINs per call) this costs one call per item, so an uncapped pass over
     * a full result page would stall on the rate limit.
     */
    private int maxOfferLookups = 5;

    /** Built-in fallback order when {@code search-marketplace-ids} is not set. */
    private static final List<AmazonMarketplace> DEFAULT_FALLBACK_ORDER = List.of(
            AmazonMarketplace.US, AmazonMarketplace.UK, AmazonMarketplace.DE,
            AmazonMarketplace.CA, AmazonMarketplace.IN, AmazonMarketplace.JP,
            AmazonMarketplace.AU, AmazonMarketplace.FR, AmazonMarketplace.IT,
            AmazonMarketplace.ES);

    /** Resolves the SP-API base endpoint for the configured region. */
    public String getEndpoint() {
        return switch (region == null ? "NA" : region.toUpperCase()) {
            case "EU" -> "https://sellingpartnerapi-eu.amazon.com";
            case "FE" -> "https://sellingpartnerapi-fe.amazon.com";
            default -> "https://sellingpartnerapi-na.amazon.com";
        };
    }

    /**
     * The primary marketplace — the configured {@link #marketplaceId}, resolved
     * against the known registry. Falls back to US if the id is unrecognised.
     */
    public AmazonMarketplace getPrimaryMarketplace() {
        return AmazonMarketplace.find(marketplaceId).orElse(AmazonMarketplace.US);
    }

    /**
     * The ordered candidate marketplaces for one lookup: the primary first, then
     * the configured (or built-in) fallbacks, de-duplicated. Not capped — the
     * caller applies {@link #getMaxRegionAttempts()} to the regions it actually
     * queries, so unauthorized regions it skips do not eat the budget. Returns
     * just the primary when region fallback is off.
     */
    public List<AmazonMarketplace> resolveSearchMarketplaces() {
        AmazonMarketplace primary = getPrimaryMarketplace();
        if (!regionFallbackEnabled) {
            return List.of(primary);
        }
        Set<AmazonMarketplace> ordered = new LinkedHashSet<>();
        ordered.add(primary);
        if (searchMarketplaceIds.isEmpty()) {
            ordered.addAll(DEFAULT_FALLBACK_ORDER);
        } else {
            searchMarketplaceIds.stream()
                    .map(AmazonMarketplace::find)
                    .flatMap(Optional::stream)
                    .forEach(ordered::add);
        }
        return List.copyOf(ordered);
    }

    /** True only when enabled AND all required credentials are present. */
    public boolean isFullyConfigured() {
        return enabled
                && hasText(clientId) && hasText(clientSecret)
                && hasText(refreshToken) && hasText(marketplaceId);
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
