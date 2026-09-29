package com.priceintel.backend.ai;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Wires the OpenAI HTTP client with timeouts and enables its properties.
 */
@Configuration
@EnableConfigurationProperties(OpenAiProperties.class)
public class OpenAiConfig {

    @Bean("openAiRestClient")
    public RestClient openAiRestClient(OpenAiProperties props) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()))
                .withReadTimeout(Duration.ofMillis(props.getReadTimeoutMs()));
        return RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }
}
