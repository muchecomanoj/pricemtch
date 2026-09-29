package com.priceintel.backend.marketplace.scraper;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.priceintel.backend.exception.MarketplaceApiException;

import lombok.extern.slf4j.Slf4j;

/**
 * Calls the in-house Amazon page scraper.
 *
 * <p>One request at a time. The service runs on Flask's development server,
 * which handles a single request and queues the rest, and every call launches a
 * Chrome. Firing several at once would not make them faster — it would stack
 * browsers on one machine and time them all out together.</p>
 */
@Slf4j
@Component
public class AmazonScraperClient {

    /** Amazon domains the service supports, by ISO country code. */
    private static final Map<String, String> DOMAIN_BY_COUNTRY = Map.of(
            "US", "com",
            "CA", "ca",
            "GB", "co.uk",
            "DE", "de",
            "IN", "in");

    /** How long a caller waits for the queue before giving up. */
    private static final long QUEUE_WAIT_SECONDS = 120;

    private final AmazonScraperProperties props;
    private final RestClient restClient;
    private final Semaphore oneAtATime = new Semaphore(1, true);

    public AmazonScraperClient(AmazonScraperProperties props,
                               @Qualifier("amazonScraperRestClient") RestClient restClient) {
        this.props = props;
        this.restClient = restClient;
    }

    /** The service's domain for a country code, or null where it has none. */
    public static String domainFor(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return null;
        }
        return DOMAIN_BY_COUNTRY.get(countryCode.trim().toUpperCase(Locale.ROOT));
    }

    /** The country code a returned domain belongs to, or null. */
    public static String countryForDomain(String domain) {
        if (domain == null) {
            return null;
        }
        String d = domain.trim().toLowerCase(Locale.ROOT);
        return DOMAIN_BY_COUNTRY.entrySet().stream()
                .filter(e -> e.getValue().equals(d))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
    }

    /**
     * One ASIN from one storefront.
     *
     * @return the raw JSON body, or null when the service has the ASIN on no
     *         marketplace (its 404). Not found is an answer, not a failure.
     */
    public String getProduct(String asin, String domain) {
        if (!props.isFullyConfigured()) {
            throw new MarketplaceApiException(
                    "The Amazon scraper is not configured. Set amazon.scraper.enabled=true "
                            + "and amazon.scraper.base-url.");
        }
        boolean acquired;
        try {
            acquired = oneAtATime.tryAcquire(QUEUE_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MarketplaceApiException("Interrupted waiting for the Amazon scraper", e);
        }
        if (!acquired) {
            throw new MarketplaceApiException(
                    "The Amazon scraper is busy — it serves one request at a time and the queue "
                            + "did not clear within " + QUEUE_WAIT_SECONDS + "s.");
        }
        long start = System.currentTimeMillis();
        try {
            String body = restClient.post()
                    .uri(props.getBaseUrl() + "/api/product")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("asin", asin, "domain", domain))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
            log.info("Amazon scraper returned {} on {} in {}ms",
                    asin, domain, System.currentTimeMillis() - start);
            return body;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                log.info("Amazon scraper: {} is on none of its marketplaces ({}ms)",
                        asin, System.currentTimeMillis() - start);
                return null;
            }
            throw new MarketplaceApiException("Amazon scraper HTTP "
                    + e.getStatusCode().value() + ": " + truncate(e.getResponseBodyAsString()), e);
        } catch (ResourceAccessException e) {
            // A browser-driven call is slow by nature; a timeout here usually
            // means the service is queueing, not that the ASIN is unavailable.
            throw new MarketplaceApiException(
                    "Amazon scraper unreachable or too slow: " + e.getMessage(), e);
        } finally {
            oneAtATime.release();
        }
    }

    private String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }
}
