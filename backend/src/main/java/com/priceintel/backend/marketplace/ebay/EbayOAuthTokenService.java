package com.priceintel.backend.marketplace.ebay;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.priceintel.backend.exception.MarketplaceApiException;

import lombok.extern.slf4j.Slf4j;

/**
 * Obtains and caches an eBay OAuth application access token using the
 * client-credentials grant (Basic auth with clientId:clientSecret). This is the
 * OAuth handling for the Browse API.
 */
@Slf4j
@Service
public class EbayOAuthTokenService {

    private final EbayApiProperties props;
    private final RestClient oauthRestClient;

    private volatile String cachedToken;
    private volatile Instant expiresAt = Instant.EPOCH;

    private static final long EXPIRY_SKEW_SECONDS = 60;

    /** Attempts at the token endpoint before giving up on a network failure. */
    private static final int TOKEN_ATTEMPTS = 3;

    private static final long TOKEN_RETRY_DELAY_MS = 1000;

    public EbayOAuthTokenService(EbayApiProperties props,
                                 @Qualifier("ebayOAuthRestClient") RestClient oauthRestClient) {
        this.props = props;
        this.oauthRestClient = oauthRestClient;
    }

    /**
     * Discards the cached token, forcing the next call to fetch a new one.
     *
     * <p>Must be called whenever the credentials or the environment change. A
     * token is bound to the keys and the host that issued it, and this one is
     * held for two hours — so switching SANDBOX to PRODUCTION and saving new
     * keys otherwise keeps sending the old sandbox token to the production host,
     * which answers HTTP 401. The symptom is badly misleading: the credentials
     * are correct, the connector reports healthy because a token exists, and
     * every search fails as though the keys were wrong.</p>
     */
    public synchronized void invalidate() {
        cachedToken = null;
        expiresAt = Instant.EPOCH;
        log.info("eBay access token invalidated — next call will obtain a fresh one");
    }

    public synchronized String getAccessToken() {
        if (!props.isFullyConfigured()) {
            throw new MarketplaceApiException(
                    "eBay Browse API is not configured. Set ebay.browse-api.enabled=true and provide "
                            + "clientId, clientSecret, and marketplaceId.");
        }
        if (cachedToken != null && Instant.now().isBefore(expiresAt)) {
            return cachedToken;
        }
        return refreshToken();
    }

    /**
     * Fetches a token, retrying a network failure.
     *
     * <p>Every eBay call depends on this one, and it runs roughly twice a day —
     * whenever the two-hour token expires. Without a retry a single slow
     * handshake takes the whole search down and reports eBay as broken: exactly
     * that happened when a connect took 7 seconds against a 5-second limit,
     * while eBay itself was reachable and healthy.</p>
     *
     * <p>Only I/O failures are retried. A rejected credential fails immediately,
     * because asking again with the same wrong key wastes the user's time to
     * arrive at the same answer.</p>
     */
    private String refreshToken() {
        log.info("Requesting new eBay OAuth application access token");

        String basic = Base64.getEncoder().encodeToString(
                (props.getClientId() + ":" + props.getClientSecret()).getBytes(StandardCharsets.UTF_8));

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("scope", props.getScope());

        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= TOKEN_ATTEMPTS; attempt++) {
            try {
                JsonNode body = oauthRestClient.post()
                        .uri(props.getOauthUrl())
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form)
                        .retrieve()
                        .body(JsonNode.class);

                if (body == null || !body.hasNonNull("access_token")) {
                    throw new MarketplaceApiException("eBay token response had no access_token");
                }
                String token = body.get("access_token").asText();
                long expiresIn = body.path("expires_in").asLong(7200);
                this.cachedToken = token;
                this.expiresAt = Instant.now().plusSeconds(Math.max(0, expiresIn - EXPIRY_SKEW_SECONDS));
                log.info("Obtained eBay access token (expires in {}s){}", expiresIn,
                        attempt > 1 ? " on attempt " + attempt : "");
                return token;
            } catch (org.springframework.web.client.RestClientResponseException e) {
                // eBay answered and said no. Retrying changes nothing.
                throw new MarketplaceApiException("eBay rejected the credentials (HTTP "
                        + e.getStatusCode().value() + "). Check the App ID and Cert ID, and that "
                        + "they match the selected environment.", e);
            } catch (MarketplaceApiException e) {
                throw e;
            } catch (RuntimeException e) {
                lastFailure = e;
                log.warn("eBay token attempt {} of {} failed: {}",
                        attempt, TOKEN_ATTEMPTS, e.getMessage());
                if (attempt < TOKEN_ATTEMPTS) {
                    try {
                        Thread.sleep(TOKEN_RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        throw new MarketplaceApiException("Could not reach eBay to obtain an access token after "
                + TOKEN_ATTEMPTS + " attempts. The network or eBay is slow right now — the "
                + "credentials were never checked.", lastFailure);
    }
}
