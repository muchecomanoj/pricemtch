package com.priceintel.backend.service.impl;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.priceintel.backend.dto.request.NotificationChannelsRequest;
import com.priceintel.backend.dto.response.NotificationChannelsResponse;
import com.priceintel.backend.entity.TenantNotificationChannels;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.repository.TenantNotificationChannelsRepository;
import com.priceintel.backend.security.TenantContext;
import com.priceintel.backend.utils.EncryptionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages a tenant's chat delivery settings and sends alerts to them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationChannelService {

    private final TenantNotificationChannelsRepository repo;
    private final EncryptionService encryption;
    private final WebhookNotifier webhooks;

    // ---------- settings ----------

    @Transactional(readOnly = true)
    public NotificationChannelsResponse get() {
        TenantNotificationChannels c = findForTenant(requireTenant()).orElse(null);
        return NotificationChannelsResponse.builder()
                .slack(describe(c != null && c.isSlackEnabled(),
                        c == null ? null : c.getSlackWebhookUrl()))
                .teams(describe(c != null && c.isTeamsEnabled(),
                        c == null ? null : c.getTeamsWebhookUrl()))
                .build();
    }

    @Transactional
    public NotificationChannelsResponse update(NotificationChannelsRequest request) {
        Long tenantId = requireTenant();
        TenantNotificationChannels c = findForTenant(tenantId)
                .orElseGet(() -> TenantNotificationChannels.builder().tenantId(tenantId).build());

        if (request.getSlackWebhookUrl() != null) {
            c.setSlackWebhookUrl(storeUrl(request.getSlackWebhookUrl(), "Slack"));
        }
        if (request.getTeamsWebhookUrl() != null) {
            c.setTeamsWebhookUrl(storeUrl(request.getTeamsWebhookUrl(), "Teams"));
        }
        if (request.getSlackEnabled() != null) {
            c.setSlackEnabled(request.getSlackEnabled());
        }
        if (request.getTeamsEnabled() != null) {
            c.setTeamsEnabled(request.getTeamsEnabled());
        }
        // Enabling a channel with no URL would fail silently on every alert.
        if (c.isSlackEnabled() && isBlank(c.getSlackWebhookUrl())) {
            throw new BadRequestException("Add a Slack webhook URL before enabling Slack alerts.");
        }
        if (c.isTeamsEnabled() && isBlank(c.getTeamsWebhookUrl())) {
            throw new BadRequestException("Add a Teams webhook URL before enabling Teams alerts.");
        }
        repo.save(c);
        return get();
    }

    /**
     * Sends a test message so a mistyped URL is caught on the settings screen.
     *
     * <p>Without this a bad URL fails invisibly: the alert fires, the POST 404s,
     * and nobody finds out until they ask why they never heard about a price
     * drop.</p>
     */
    @Transactional(readOnly = true)
    public boolean sendTest(String channel) {
        TenantNotificationChannels c = findForTenant(requireTenant())
                .orElseThrow(() -> new BadRequestException("No notification channels configured yet."));
        WebhookNotifier.Channel target = parseChannel(channel);
        String url = target == WebhookNotifier.Channel.SLACK
                ? c.getSlackWebhookUrl() : c.getTeamsWebhookUrl();
        if (isBlank(url)) {
            throw new BadRequestException("No " + target + " webhook URL is configured.");
        }
        return webhooks.send(target, decrypt(url),
                "Price Intelligence test message",
                "If you can read this, alerts will reach this channel.", null);
    }

    // ---------- delivery ----------

    /** Fans an alert out to whichever chat channels this tenant has enabled. */
    @Transactional(readOnly = true)
    public void deliver(Long tenantId, String title, String body, String link) {
        if (tenantId == null) {
            return;
        }
        findForTenant(tenantId).ifPresent(c -> {
            if (c.isSlackEnabled() && !isBlank(c.getSlackWebhookUrl())) {
                webhooks.send(WebhookNotifier.Channel.SLACK,
                        decrypt(c.getSlackWebhookUrl()), title, body, link);
            }
            if (c.isTeamsEnabled() && !isBlank(c.getTeamsWebhookUrl())) {
                webhooks.send(WebhookNotifier.Channel.TEAMS,
                        decrypt(c.getTeamsWebhookUrl()), title, body, link);
            }
        });
    }

    // ---------- helpers ----------

    private Optional<TenantNotificationChannels> findForTenant(Long tenantId) {
        return repo.findByTenantId(tenantId);
    }

    private Long requireTenant() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BadRequestException(
                    "Notification channels belong to a client account. Sign in as a client user.");
        }
        return tenantId;
    }

    /** Encrypts a URL for storage; an empty string clears it. */
    private String storeUrl(String raw, String label) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (!trimmed.startsWith("https://")) {
            throw new BadRequestException(label + " webhook URL must start with https://");
        }
        return encryption.encrypt(trimmed);
    }

    private String decrypt(String stored) {
        try {
            return encryption.decrypt(stored);
        } catch (RuntimeException e) {
            log.warn("Could not decrypt a stored webhook URL — was the crypto secret changed?");
            return null;
        }
    }

    private NotificationChannelsResponse.Channel describe(boolean enabled, String storedUrl) {
        boolean configured = !isBlank(storedUrl);
        return NotificationChannelsResponse.Channel.builder()
                .enabled(enabled)
                .configured(configured)
                .webhookUrlMasked(configured ? mask(decrypt(storedUrl)) : null)
                .build();
    }

    /** Shows enough to recognise the URL, never enough to post with. */
    private String mask(String url) {
        if (url == null || url.length() < 12) {
            return "configured";
        }
        int slash = url.indexOf('/', "https://".length());
        String host = slash > 0 ? url.substring(0, slash) : url;
        return host + "/…/" + url.substring(url.length() - 5);
    }

    private WebhookNotifier.Channel parseChannel(String channel) {
        try {
            return WebhookNotifier.Channel.valueOf(
                    channel == null ? "" : channel.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown channel: " + channel + ". Use SLACK or TEAMS.");
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
