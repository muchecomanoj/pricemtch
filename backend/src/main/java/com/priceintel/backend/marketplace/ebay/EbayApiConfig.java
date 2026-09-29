package com.priceintel.backend.marketplace.ebay;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.priceintel.backend.marketplace.amazon.SimpleRateLimiter;

/**
 * Wires the eBay Browse API HTTP clients (with timeouts) and its rate limiter.
 */
@Configuration
@EnableConfigurationProperties(EbayApiProperties.class)
public class EbayApiConfig {

    private ClientHttpRequestFactory timeoutFactory(EbayApiProperties props) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()))
                .withReadTimeout(Duration.ofMillis(props.getReadTimeoutMs()));
        return ClientHttpRequestFactories.get(settings);
    }

    @Bean("ebayBrowseRestClient")
    public RestClient ebayBrowseRestClient(EbayApiProperties props) {
        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(timeoutFactory(props))
                .build();
    }

    @Bean("ebayOAuthRestClient")
    public RestClient ebayOAuthRestClient(EbayApiProperties props) {
        return RestClient.builder()
                .requestFactory(timeoutFactory(props))
                .build();
    }

    @Bean("ebayRateLimiter")
    public SimpleRateLimiter ebayRateLimiter(EbayApiProperties props) {
        return new SimpleRateLimiter(props.getRatePerSecond());
    }
}
