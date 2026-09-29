package com.priceintel.backend.marketplace.keepa;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for the Keepa API integration (Amazon product & price data),
 * bound from properties prefixed {@code keepa}. Keepa needs only an API key —
 * no seller authorization — which makes it well suited to competitor pricing.
 * Disabled by default; set {@code keepa.enabled=true} and {@code keepa.api-key}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "keepa")
public class KeepaProperties {

    /** Master switch. When false the integration is inactive. */
    private boolean enabled = false;

    /** Keepa API key (from a keepa.com subscription). */
    private String apiKey;

    /**
     * Keepa marketplace domain id: 1=US, 2=UK, 3=DE, 4=FR, 5=JP, 6=CA, 8=IT,
     * 9=ES, 10=IN, 11=MX. Defaults to US.
     */
    private int domain = 1;

    private String baseUrl = "https://api.keepa.com";

    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 20000;
    private int maxRetries = 3;
    /** Keepa allows ~ limited requests/sec depending on plan; keep it gentle. */
    private double ratePerSecond = 1;

    /** True only when enabled AND an API key is present. */
    public boolean isFullyConfigured() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    /** ISO currency for the configured domain (used to label prices). */
    public String currency() {
        return switch (domain) {
            case 2 -> "GBP";
            case 3, 4, 8, 9 -> "EUR";
            case 5 -> "JPY";
            case 6 -> "CAD";
            case 10 -> "INR";
            case 11 -> "MXN";
            default -> "USD";
        };
    }
}
