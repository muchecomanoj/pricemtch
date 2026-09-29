package com.priceintel.backend.marketplace.keepa;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.priceintel.backend.marketplace.amazon.SimpleRateLimiter;

/** Wires the Keepa HTTP client with timeouts and a gentle rate limiter. */
@Configuration
@EnableConfigurationProperties(KeepaProperties.class)
public class KeepaConfig {

    @Bean("keepaRestClient")
    public RestClient keepaRestClient(KeepaProperties props) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()))
                .withReadTimeout(Duration.ofMillis(props.getReadTimeoutMs()));
        ClientHttpRequestFactory factory = ClientHttpRequestFactories.get(settings);
        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    @Bean
    public SimpleRateLimiter keepaRateLimiter(KeepaProperties props) {
        return new SimpleRateLimiter(props.getRatePerSecond());
    }
}
