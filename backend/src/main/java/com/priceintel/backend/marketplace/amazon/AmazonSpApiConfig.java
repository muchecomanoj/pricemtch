package com.priceintel.backend.marketplace.amazon;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Wires the Amazon SP-API HTTP clients with connect/read timeouts, and enables
 * the typed configuration properties.
 */
@Configuration
@EnableConfigurationProperties(AmazonSpApiProperties.class)
public class AmazonSpApiConfig {

    private ClientHttpRequestFactory timeoutFactory(AmazonSpApiProperties props) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()))
                .withReadTimeout(Duration.ofMillis(props.getReadTimeoutMs()));
        return ClientHttpRequestFactories.get(settings);
    }

    /** Client for the SP-API endpoints (catalog / pricing / fees). */
    @Bean("spApiRestClient")
    public RestClient spApiRestClient(AmazonSpApiProperties props) {
        return RestClient.builder()
                .baseUrl(props.getEndpoint())
                .requestFactory(timeoutFactory(props))
                .build();
    }

    /** Client for the LWA OAuth token endpoint. */
    @Bean("lwaRestClient")
    public RestClient lwaRestClient(AmazonSpApiProperties props) {
        return RestClient.builder()
                .requestFactory(timeoutFactory(props))
                .build();
    }

    @Bean
    public SimpleRateLimiter amazonRateLimiter(AmazonSpApiProperties props) {
        return new SimpleRateLimiter(props.getRatePerSecond());
    }
}
