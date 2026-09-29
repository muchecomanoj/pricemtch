package com.priceintel.backend.payment;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceintel.backend.exception.BadRequestException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Thin Stripe client over the REST API (no SDK dependency, consistent with the
 * other integrations). Creates Checkout Sessions and verifies webhook
 * signatures. Only used when Stripe is configured; otherwise the flow uses a
 * mock path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StripeService {

    private final StripeProperties props;
    @Qualifier("stripeRestClient")
    private final RestClient stripeRestClient;
    private final ObjectMapper objectMapper;

    public boolean isConfigured() {
        return props.isConfigured();
    }

    public record CheckoutSession(String id, String url) {
    }

    /**
     * Creates a one-time Checkout Session for the first billing period. We track
     * trial/subscription dates ourselves; {@code clientReference} is our
     * onboarding token so the webhook can identify the tenant.
     */
    public CheckoutSession createCheckoutSession(long amountCents, String description,
                                                 String clientReference, String successUrl, String cancelUrl) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "payment");
        form.add("success_url", successUrl);
        form.add("cancel_url", cancelUrl);
        form.add("client_reference_id", clientReference);
        form.add("line_items[0][quantity]", "1");
        form.add("line_items[0][price_data][currency]", props.getCurrency());
        form.add("line_items[0][price_data][unit_amount]", String.valueOf(amountCents));
        form.add("line_items[0][price_data][product_data][name]", description);

        try {
            JsonNode resp = stripeRestClient.post()
                    .uri("/v1/checkout/sessions")
                    .header("Authorization", "Bearer " + props.getSecretKey())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            if (resp == null || !resp.hasNonNull("id")) {
                throw new BadRequestException("Stripe did not return a checkout session");
            }
            return new CheckoutSession(resp.get("id").asText(), resp.path("url").asText(null));
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Stripe checkout session failed", e);
            throw new BadRequestException("Could not start payment: " + e.getMessage());
        }
    }

    public record SessionStatus(String id, String paymentStatus, String status, String clientReferenceId) {
        public boolean isPaid() {
            return "paid".equalsIgnoreCase(paymentStatus) || "complete".equalsIgnoreCase(status);
        }
    }

    /**
     * Retrieves a Checkout Session so the success page can confirm payment
     * directly (without waiting for the webhook). Returns the payment status and
     * our client_reference_id.
     */
    public SessionStatus retrieveSession(String sessionId) {
        try {
            JsonNode resp = stripeRestClient.get()
                    .uri("/v1/checkout/sessions/{id}", sessionId)
                    .header("Authorization", "Bearer " + props.getSecretKey())
                    .retrieve()
                    .body(JsonNode.class);
            if (resp == null) {
                throw new BadRequestException("Stripe returned no session");
            }
            return new SessionStatus(
                    resp.path("id").asText(null),
                    resp.path("payment_status").asText(null),
                    resp.path("status").asText(null),
                    resp.path("client_reference_id").asText(null));
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Stripe session retrieve failed for {}", sessionId, e);
            throw new BadRequestException("Could not verify payment: " + e.getMessage());
        }
    }

    /** Verifies the Stripe-Signature header (t=..,v1=..) against the raw body. */
    public boolean verifySignature(String payload, String sigHeader) {
        if (props.getWebhookSecret() == null || props.getWebhookSecret().isBlank() || sigHeader == null) {
            return false;
        }
        try {
            Map<String, String> parts = new java.util.HashMap<>();
            for (String item : sigHeader.split(",")) {
                String[] kv = item.split("=", 2);
                if (kv.length == 2) {
                    parts.put(kv[0].trim(), kv[1].trim());
                }
            }
            String timestamp = parts.get("t");
            String expected = parts.get("v1");
            if (timestamp == null || expected == null) {
                return false;
            }
            String signedPayload = timestamp + "." + payload;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(props.getWebhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return constantTimeEquals(hex.toString(), expected);
        } catch (Exception e) {
            log.warn("Stripe signature verification error: {}", e.getMessage());
            return false;
        }
    }

    /** For a checkout.session.completed event, returns our client_reference_id. */
    public String extractClientReference(String eventPayload) {
        try {
            JsonNode event = objectMapper.readTree(eventPayload);
            if (!"checkout.session.completed".equals(event.path("type").asText())) {
                return null;
            }
            return event.path("data").path("object").path("client_reference_id").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
