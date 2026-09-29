package com.priceintel.backend.payment;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(StripeProperties.class)
public class StripeConfig {

    @Bean("stripeRestClient")
    public RestClient stripeRestClient(StripeProperties props) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(5))
                .withReadTimeout(Duration.ofSeconds(20));
        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }
}
