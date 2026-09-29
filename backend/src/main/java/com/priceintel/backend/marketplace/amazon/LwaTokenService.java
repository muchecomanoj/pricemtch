package com.priceintel.backend.marketplace.amazon;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.priceintel.backend.exception.MarketplaceApiException;

import lombok.extern.slf4j.Slf4j;

/**
 * Manages the Login-with-Amazon (LWA) OAuth access token. Exchanges the
 * long-lived refresh token for a short-lived access token, caches it, and
 * refreshes automatically shortly before expiry.
 *
 * <p>This is the "OAuth" + "token refresh" handling for the SP-API integration.</p>
 */
@Slf4j
@Service
public class LwaTokenService {

    private final AmazonSpApiProperties props;
    private final RestClient lwaRestClient;

    private volatile String cachedToken;
    private volatile Instant expiresAt = Instant.EPOCH;

    /** Refresh this many seconds before the token actually expires. */
    private static final long EXPIRY_SKEW_SECONDS = 60;

    public LwaTokenService(AmazonSpApiProperties props,
                           @Qualifier("lwaRestClient") RestClient lwaRestClient) {
        this.props = props;
        this.lwaRestClient = lwaRestClient;
    }

    /** Returns a valid access token, refreshing it if missing or near expiry. */
    public synchronized String getAccessToken() {
        if (!props.isFullyConfigured()) {
            throw new MarketplaceApiException(
                    "Amazon SP-API is not configured. Set amazon.sp-api.enabled=true and provide "
                            + "clientId, clientSecret, refreshToken, and marketplaceId.");
        }
        if (cachedToken != null && Instant.now().isBefore(expiresAt)) {
            return cachedToken;
        }
        return refreshToken();
    }

    private String refreshToken() {
        log.info("Requesting new LWA access token (refresh)");

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", props.getRefreshToken());
        form.add("client_id", props.getClientId());
        form.add("client_secret", props.getClientSecret());

        try {
            JsonNode body = lwaRestClient.post()
                    .uri(props.getLwaTokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);

            if (body == null || !body.hasNonNull("access_token")) {
                throw new MarketplaceApiException("LWA token response had no access_token");
            }
            String token = body.get("access_token").asText();
            long expiresIn = body.path("expires_in").asLong(3600);
            this.cachedToken = token;
            this.expiresAt = Instant.now().plusSeconds(Math.max(0, expiresIn - EXPIRY_SKEW_SECONDS));
            log.info("Obtained LWA access token (expires in {}s)", expiresIn);
            return token;
        } catch (MarketplaceApiException e) {
            throw e;
        } catch (Exception e) {
            throw new MarketplaceApiException("Failed to obtain LWA access token: " + e.getMessage(), e);
        }
    }

    /** Clears the cached token (used by health checks / tests). */
    public synchronized void invalidate() {
        cachedToken = null;
        expiresAt = Instant.EPOCH;
    }
}
