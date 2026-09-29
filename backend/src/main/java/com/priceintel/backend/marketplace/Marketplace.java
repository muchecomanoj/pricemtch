package com.priceintel.backend.marketplace;

/**
 * The marketplaces the platform can talk to. Each has one adapter implementation
 * backed by a real API — there is deliberately no mock source, so every price
 * shown is one a customer could actually pay.
 */
public enum Marketplace {
    AMAZON,
    EBAY
}
