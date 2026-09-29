package com.priceintel.backend.marketplace.scraper;

import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * The in-house Amazon page scraper, used where SP-API cannot go.
 *
 * <p>Amazon authorises an SP-API account per marketplace. This one is
 * authorised for the US and Canada; every other storefront answers HTTP 403. So
 * an ASIN searched in the UK, Germany or India cannot be looked up officially at
 * all, and the scraper reads the public product page instead.</p>
 *
 * <p>It is deliberately narrow: a fallback for ASIN lookups outside the
 * authorised marketplaces, never a replacement for the official API where that
 * works. Off unless {@code amazon.scraper.enabled=true}.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "amazon.scraper")
public class AmazonScraperProperties {

    /** Master switch. When false, an unauthorised region simply returns nothing. */
    private boolean enabled = false;

    /** Base URL of the scraper service, e.g. {@code http://192.168.10.189:5000}. */
    private String baseUrl = "";

    /**
     * Marketplaces the SP-API account is authorised for, which the scraper must
     * never be used for.
     *
     * <p>The official API is better in every way where it works: faster, no
     * browser, and a Buy Box price rather than a page reading. This list is the
     * boundary between the two.</p>
     */
    private List<String> spApiRegions = List.of("US", "CA");

    private int connectTimeoutMs = 10_000;

    /**
     * Read timeout. Their documentation asks for over 60s: every call launches a
     * real Chrome, taking 5-15s when the ASIN is found on the first storefront
     * and ~37s when it is on none of them.
     */
    private int readTimeoutMs = 90_000;

    /**
     * Whether a converted price may be used.
     *
     * <p>Off by default, and it matters. Amazon prices by the visitor's IP, so a
     * UK page served to a scraper in India quotes an import price including duty
     * and international shipping. The service converts that to GBP and labels it
     * {@code converted} — its own documentation calls it an estimate, not the
     * domestic shelf price. Letting an estimate into the price statistics would
     * distort every median, recommendation and alert built on them.</p>
     *
     * <p>Turn it on only once the service has an in-country proxy for the
     * marketplace, at which point it reports {@code ok} anyway.</p>
     */
    private boolean acceptConvertedPrices = false;

    public boolean isFullyConfigured() {
        return enabled && baseUrl != null && !baseUrl.isBlank();
    }

    /** Whether this storefront is covered by the official API rather than the scraper. */
    public boolean isSpApiRegion(String countryCode) {
        if (countryCode == null) {
            return true;
        }
        String code = countryCode.trim().toUpperCase(Locale.ROOT);
        return spApiRegions.stream().anyMatch(r -> r.trim().equalsIgnoreCase(code));
    }
}
