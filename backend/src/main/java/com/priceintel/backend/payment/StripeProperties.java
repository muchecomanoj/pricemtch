package com.priceintel.backend.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Stripe configuration (prefix {@code stripe}). Disabled by default; when off,
 * checkout runs in mock mode so the onboarding flow is testable without keys.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "stripe")
public class StripeProperties {

    private boolean enabled = false;
    private String secretKey;
    private String webhookSecret;
    private String currency = "usd";
    private String baseUrl = "https://api.stripe.com";

    public boolean isConfigured() {
        return enabled && secretKey != null && !secretKey.isBlank();
    }
}
