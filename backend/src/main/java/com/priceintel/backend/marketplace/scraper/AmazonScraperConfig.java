package com.priceintel.backend.marketplace.scraper;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Wires the scraper client with the long timeouts a browser-driven call needs. */
@Configuration
@EnableConfigurationProperties(AmazonScraperProperties.class)
public class AmazonScraperConfig {

    @Bean("amazonScraperRestClient")
    public RestClient amazonScraperRestClient(AmazonScraperProperties props) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()))
                .withReadTimeout(Duration.ofMillis(props.getReadTimeoutMs()));
        ClientHttpRequestFactory factory = ClientHttpRequestFactories.get(settings);
        return RestClient.builder().requestFactory(factory).build();
    }
}
