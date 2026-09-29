package com.priceintel.backend.service;

import com.priceintel.backend.dto.response.ProductResponse;

/**
 * Fetches a product from Amazon, stores the raw JSON, and persists the canonical
 * product (Phase 7).
 */
public interface AmazonIngestionService {

    /**
     * Pulls Catalog + Pricing for an ASIN, stores each raw payload, builds the
     * canonical product, saves it, and returns the normalized product.
     */
    ProductResponse ingestByAsin(String asin);
}
