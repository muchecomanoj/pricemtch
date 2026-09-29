package com.priceintel.backend.service;

import java.util.Map;

import com.priceintel.backend.dto.response.ProductResponse;

/**
 * Fetches Amazon product & price data from Keepa, stores the raw JSON, and
 * persists the canonical product. Keepa is the Amazon data source.
 */
public interface KeepaIngestionService {

    /** Fetch a Keepa product by ASIN, store raw, upsert the canonical product. */
    ProductResponse ingestByAsin(String asin);

    /** Current price + recent price history for an ASIN (does not persist a product). */
    Map<String, Object> priceByAsin(String asin);
}
