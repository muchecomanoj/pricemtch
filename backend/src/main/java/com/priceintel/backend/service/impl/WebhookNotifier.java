package com.priceintel.backend.service.impl;

import java.time.Duration;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

/**
 * Posts alert messages to a chat webhook.
 *
 * <p>Slack and Teams both accept a simple JSON POST to a URL the tenant
 * supplies, so no OAuth or stored account is involved — which is why these are
 * cheap to support and worth having.</p>
 */
@Slf4j
@Component
public class WebhookNotifier {

    private final RestClient client;
    private final ObjectMapper mapper;

    public WebhookNotifier(ObjectMapper mapper) {
        this.mapper = mapper;
        this.client = RestClient.builder()
                .requestFactory(factory())
                .build();
    }

    private static org.springframework.http.client.ClientHttpRequestFactory factory() {
        var f = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        // Alert delivery must not hold up the evaluation sweep behind a chat
        // provider having a slow day.
        f.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        f.setReadTimeout((int) Duration.ofSeconds(10).toMillis());
        return f;
    }

    /** What the destination expects; the payload shape differs slightly. */
    public enum Channel {
        SLACK, TEAMS
    }

    /**
     * Sends one message. Returns false rather than throwing — a chat provider
     * being unreachable should not lose the alert, which is already recorded
     * in-app regardless.
     *
     * @return true when the webhook accepted the message
     */
    public boolean send(Channel channel, String webhookUrl, String title, String body, String link) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return false;
        }
        try {
            client.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload(channel, title, body, link))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            // Deliberately does not log the URL — it is a credential.
            log.warn("{} webhook delivery failed: {}", channel, e.getMessage());
            return false;
        }
    }

    private String payload(Channel channel, String title, String body, String link) {
        String text = title + "\n" + body + (link == null || link.isBlank() ? "" : "\n" + link);
        ObjectNode node = mapper.createObjectNode();
        if (channel == Channel.TEAMS) {
            // Office 365 connector card: "text" alone renders, but a summary is
            // required or some Teams clients reject the card outright.
            node.put("@type", "MessageCard");
            node.put("@context", "https://schema.org/extensions");
            node.put("summary", title);
            node.put("title", title);
            node.put("text", body + (link == null || link.isBlank() ? "" : "\n\n" + link));
        } else {
            node.put("text", text);
        }
        return node.toString();
    }
}
