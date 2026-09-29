package com.priceintel.backend.constants;

import java.util.List;
import java.util.Set;

/**
 * Configurable marketplace providers (platform-global). Each declares its
 * display info, supported capabilities, and which credential keys are secret
 * (so the API never echoes them back).
 */
public enum MarketplaceProvider {

    AMAZON("Amazon",
            "Amazon SP-API — create an app in Seller Central, then generate Login-with-Amazon (LWA) credentials.",
            List.of("Search", "Listing details", "Pricing", "Fee estimate", "Owned sales", "Owned inventory"),
            Set.of("sellerToken", "lwaClientSecret", "lwaRefreshToken")),

    EBAY("eBay",
            "eBay Buy Browse API — create an application in the eBay Developer Program.",
            List.of("Search", "Listing details", "Pricing", "Sold history"),
            Set.of("certId")),

    KEEPA("Amazon (Keepa)",
            "Keepa API — Amazon product & price data by ASIN (key only, no seller auth).",
            List.of("Product", "Pricing", "Price history"),
            Set.of("apiKey"));

    private final String displayName;
    private final String description;
    private final List<String> capabilities;
    private final Set<String> secretKeys;

    MarketplaceProvider(String displayName, String description,
                        List<String> capabilities, Set<String> secretKeys) {
        this.displayName = displayName;
        this.description = description;
        this.capabilities = capabilities;
        this.secretKeys = secretKeys;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public List<String> capabilities() {
        return capabilities;
    }

    public boolean isSecret(String key) {
        return secretKeys.contains(key);
    }
}
