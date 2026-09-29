package com.priceintel.backend.service;

import com.priceintel.backend.dto.response.ProductResponse;

/**
 * Fetches an item from eBay, stores the raw JSON, and persists the canonical
 * product (Phase 8).
 */
public interface EbayIngestionService {

    ProductResponse ingestByItemId(String itemId);
}
