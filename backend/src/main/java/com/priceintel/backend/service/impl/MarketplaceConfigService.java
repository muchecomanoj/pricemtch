package com.priceintel.backend.service.impl;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceintel.backend.constants.MarketplaceProvider;
import com.priceintel.backend.dto.request.MarketplaceConfigRequest;
import com.priceintel.backend.dto.response.MarketplaceStatusResponse;
import com.priceintel.backend.entity.MarketplaceConfig;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.marketplace.amazon.AmazonSpApiProperties;
import com.priceintel.backend.marketplace.amazon.LwaTokenService;
import com.priceintel.backend.marketplace.ebay.EbayApiProperties;
import com.priceintel.backend.marketplace.ebay.EbayOAuthTokenService;
import com.priceintel.backend.marketplace.keepa.KeepaProperties;
import com.priceintel.backend.repository.MarketplaceConfigRepository;
import com.priceintel.backend.utils.EncryptionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages platform-global marketplace credentials. Persists them AES-encrypted,
 * hydrates the live config beans so changes take effect without a restart, and
 * tests connections. Secret values are never returned to callers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketplaceConfigService {

    private final MarketplaceConfigRepository repo;
    private final EncryptionService crypto;
    private final ObjectMapper objectMapper;
    private final AmazonSpApiProperties amazonProps;
    private final EbayApiProperties ebayProps;
    private final KeepaProperties keepaProps;
    private final LwaTokenService lwaTokenService;
    private final EbayOAuthTokenService ebayTokenService;

    @Transactional(readOnly = true)
    public List<MarketplaceStatusResponse> list() {
        return Arrays.stream(MarketplaceProvider.values()).map(this::toStatus).toList();
    }

    @Transactional
    public MarketplaceStatusResponse save(MarketplaceProvider provider, MarketplaceConfigRequest req) {
        MarketplaceConfig cfg = repo.findByProvider(provider)
                .orElseGet(() -> MarketplaceConfig.builder().provider(provider).build());
        // Merge: blank/absent secret values keep the previously stored value.
        Map<String, String> merged = new LinkedHashMap<>(decryptCreds(cfg));
        if (req.getCredentials() != null) {
            req.getCredentials().forEach((k, v) -> {
                if (v != null && !v.isBlank()) {
                    merged.put(k, v.trim());
                }
            });
        }
        cfg.setEnabled(req.isEnabled());
        cfg.setEncryptedCredentials(crypto.encrypt(toJson(merged)));
        if (merged.isEmpty()) {
            cfg.setStatus("NOT_CONFIGURED");
        } else if (!"CONNECTED".equals(cfg.getStatus())) {
            cfg.setStatus("CONFIGURED"); // credentials saved; test to verify
        }
        repo.save(cfg);
        hydrate(provider, merged, req.isEnabled());
        log.info("Marketplace {} config saved (enabled={})", provider, req.isEnabled());
        return toStatus(provider);
    }

    @Transactional
    public MarketplaceStatusResponse testConnection(MarketplaceProvider provider) {
        MarketplaceConfig cfg = repo.findByProvider(provider)
                .orElseThrow(() -> new BadRequestException("Configure " + provider + " before testing"));
        Map<String, String> creds = decryptCreds(cfg);
        hydrate(provider, creds, true); // force-enable for the duration of the test

        String status;
        String message;
        try {
            switch (provider) {
                case AMAZON -> {
                    lwaTokenService.invalidate();
                    lwaTokenService.getAccessToken();
                }
                case EBAY -> {
                    // As with Amazon: a cached token belongs to the keys and the
                    // host that issued it, so testing new credentials while the
                    // old token is still valid tests the old ones.
                    ebayTokenService.invalidate();
                    ebayTokenService.getAccessToken();
                }
                case KEEPA -> {
                    if (keepaProps.getApiKey() == null || keepaProps.getApiKey().isBlank()) {
                        throw new BadRequestException("Keepa API key is required");
                    }
                }
            }
            status = "CONNECTED";
            message = "Connection successful.";
        } catch (Exception e) {
            status = "ERROR";
            message = e.getMessage();
            log.warn("Marketplace {} test failed: {}", provider, e.getMessage());
        }
        // Restore the persisted enabled flag.
        hydrate(provider, creds, cfg.isEnabled());
        cfg.setStatus(status);
        cfg.setLastMessage(message);
        cfg.setLastCheckedAt(Instant.now());
        repo.save(cfg);
        return toStatus(provider);
    }

    /** On startup, load any DB config into the live beans (DB overrides env vars). */
    @EventListener(ApplicationReadyEvent.class)
    public void hydrateAllOnStartup() {
        for (MarketplaceConfig cfg : repo.findAll()) {
            try {
                hydrate(cfg.getProvider(), decryptCreds(cfg), cfg.isEnabled());
                log.info("Loaded stored config for marketplace {}", cfg.getProvider());
            } catch (Exception e) {
                log.error("Could not load config for {}: {}", cfg.getProvider(), e.getMessage());
            }
        }
    }

    // ---------- hydration (creds -> live beans) ----------

    private void hydrate(MarketplaceProvider provider, Map<String, String> c, boolean enabled) {
        switch (provider) {
            case AMAZON -> {
                amazonProps.setEnabled(enabled);
                if (has(c, "lwaClientId")) amazonProps.setClientId(c.get("lwaClientId"));
                if (has(c, "lwaClientSecret")) amazonProps.setClientSecret(c.get("lwaClientSecret"));
                if (has(c, "lwaRefreshToken")) amazonProps.setRefreshToken(c.get("lwaRefreshToken"));
                if (has(c, "region")) amazonProps.setRegion(c.get("region"));
                if (has(c, "marketplaceId")) amazonProps.setMarketplaceId(c.get("marketplaceId"));
            }
            case EBAY -> {
                ebayProps.setEnabled(enabled);
                if (has(c, "appId")) ebayProps.setClientId(c.get("appId"));
                if (has(c, "certId")) ebayProps.setClientSecret(c.get("certId"));
                if (has(c, "environment")) ebayProps.setEnvironment(c.get("environment"));
                if (has(c, "marketplace")) ebayProps.setMarketplaceId(c.get("marketplace"));
                // A cached token belongs to the keys and the host that issued
                // it, and is held for two hours. Without this, saving production
                // credentials keeps sending the old sandbox token to the
                // production host — which answers 401 while the connector still
                // reports healthy, because a token does exist.
                ebayTokenService.invalidate();
            }
            case KEEPA -> {
                keepaProps.setEnabled(enabled);
                if (has(c, "apiKey")) keepaProps.setApiKey(c.get("apiKey"));
                if (has(c, "domain")) {
                    try {
                        keepaProps.setDomain(Integer.parseInt(c.get("domain")));
                    } catch (NumberFormatException ignored) {
                        // keep existing domain
                    }
                }
            }
        }
    }

    // ---------- helpers ----------

    private MarketplaceStatusResponse toStatus(MarketplaceProvider provider) {
        MarketplaceConfig cfg = repo.findByProvider(provider).orElse(null);
        Map<String, String> creds = decryptCreds(cfg);
        boolean configured = !creds.isEmpty();
        String status = cfg != null ? cfg.getStatus() : "NOT_CONFIGURED";
        return MarketplaceStatusResponse.builder()
                .code(provider.name())
                .name(provider.displayName())
                .description(provider.description())
                .enabled(cfg != null && cfg.isEnabled())
                .configured(configured)
                .status(status)
                .lastChecked(cfg != null ? cfg.getLastCheckedAt() : null)
                .lastMessage(cfg != null ? cfg.getLastMessage() : null)
                .capabilities(provider.capabilities())
                .credentials(mask(provider, creds))
                .build();
    }

    /** Non-secret values shown as-is; secret values masked to "••••1234". */
    private Map<String, String> mask(MarketplaceProvider provider, Map<String, String> creds) {
        Map<String, String> out = new LinkedHashMap<>();
        creds.forEach((k, v) -> {
            if (v == null || v.isBlank()) {
                return;
            }
            if (provider.isSecret(k)) {
                String last4 = v.length() >= 4 ? v.substring(v.length() - 4) : "";
                out.put(k, "••••" + last4);
            } else {
                out.put(k, v);
            }
        });
        return out;
    }

    private Map<String, String> decryptCreds(MarketplaceConfig cfg) {
        if (cfg == null || cfg.getEncryptedCredentials() == null) {
            return new LinkedHashMap<>();
        }
        try {
            String json = crypto.decrypt(cfg.getEncryptedCredentials());
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {
            });
        } catch (Exception e) {
            log.error("Could not decrypt credentials for {}: {}", cfg.getProvider(), e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private String toJson(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialise credentials", e);
        }
    }

    private boolean has(Map<String, String> c, String key) {
        return c.get(key) != null && !c.get(key).isBlank();
    }
}
