package com.priceintel.backend.marketplace.ebay;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for the eBay Browse API integration, bound from properties
 * prefixed {@code ebay.browse-api}. Credentials come from environment variables.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ebay.browse-api")
public class EbayApiProperties {

    private boolean enabled = false;

    /** PRODUCTION or SANDBOX. */
    private String environment = "PRODUCTION";

    private String clientId;
    private String clientSecret;
    private String marketplaceId = "EBAY_US";
    private String scope = "https://api.ebay.com/oauth/api_scope";

    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 15000;
    private int maxRetries = 3;
    private double ratePerSecond = 5;

    public String getBaseUrl() {
        return "SANDBOX".equalsIgnoreCase(environment)
                ? "https://api.sandbox.ebay.com"
                : "https://api.ebay.com";
    }

    public String getOauthUrl() {
        return getBaseUrl() + "/identity/v1/oauth2/token";
    }

    public boolean isFullyConfigured() {
        return enabled && hasText(clientId) && hasText(clientSecret) && hasText(marketplaceId);
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
