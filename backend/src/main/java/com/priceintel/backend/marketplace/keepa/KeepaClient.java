package com.priceintel.backend.marketplace.keepa;

import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.priceintel.backend.exception.MarketplaceApiException;
import com.priceintel.backend.marketplace.amazon.SimpleRateLimiter;

import lombok.extern.slf4j.Slf4j;

/**
 * Low-level Keepa API HTTP client. Injects the API key, rate-limits, retries
 * transient failures with exponential backoff, and returns the raw JSON so
 * callers can both store and parse it.
 *
 * <p>Keepa Product endpoint: {@code GET /product?key=..&domain=..&asin=..&stats=1&history=1}.</p>
 */
@Slf4j
@Component
public class KeepaClient {

    private final KeepaProperties props;
    private final RestClient keepaRestClient;
    private final SimpleRateLimiter rateLimiter;

    private static final long BASE_BACKOFF_MS = 600;

    public KeepaClient(KeepaProperties props,
                       @Qualifier("keepaRestClient") RestClient keepaRestClient,
                       @Qualifier("keepaRateLimiter") SimpleRateLimiter rateLimiter) {
        this.props = props;
        this.keepaRestClient = keepaRestClient;
        this.rateLimiter = rateLimiter;
    }

    /** Fetch a product (with stats and price history) by ASIN. */
    public String getProduct(String asin) {
        String path = "/product?key=" + props.getApiKey()
                + "&domain=" + props.getDomain()
                + "&asin=" + asin
                + "&stats=1&history=1";
        return execute("PRODUCT", () -> get(path));
    }

    /** Search Amazon by term (Keepa product-search by keywords). */
    public String search(String term) {
        String path = "/search?key=" + props.getApiKey()
                + "&domain=" + props.getDomain()
                + "&type=product&term=" + encode(term);
        return execute("SEARCH", () -> get(path));
    }

    // ---------- HTTP ----------

    private String get(String path) {
        return keepaRestClient.get()
                .uri(path)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(String.class);
    }

    private String execute(String operation, Supplier<String> call) {
        if (!props.isFullyConfigured()) {
            throw new MarketplaceApiException(
                    "Keepa is not configured. Set keepa.enabled=true and keepa.api-key.");
        }
        int attempt = 0;
        while (true) {
            attempt++;
            rateLimiter.acquire();
            long start = System.currentTimeMillis();
            try {
                String body = call.get();
                log.info("Keepa {} succeeded in {}ms (attempt {})",
                        operation, System.currentTimeMillis() - start, attempt);
                return body;
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                // 429 = out of tokens / rate limited; 5xx = transient.
                boolean transientError = status == 429 || status >= 500;
                log.warn("Keepa {} HTTP {} (attempt {}): {}", operation, status, attempt,
                        truncate(e.getResponseBodyAsString()));
                if (transientError && attempt <= props.getMaxRetries()) {
                    backoff(operation, attempt);
                    continue;
                }
                throw new MarketplaceApiException("Keepa " + operation + " failed with HTTP " + status, e);
            } catch (ResourceAccessException e) {
                log.warn("Keepa {} connection/timeout (attempt {}): {}", operation, attempt, e.getMessage());
                if (attempt <= props.getMaxRetries()) {
                    backoff(operation, attempt);
                    continue;
                }
                throw new MarketplaceApiException("Keepa " + operation + " timed out / unreachable", e);
            }
        }
    }

    private void backoff(String operation, int attempt) {
        long delay = BASE_BACKOFF_MS * (1L << (attempt - 1));
        log.info("Keepa {} retrying in {}ms", operation, delay);
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
