package com.priceintel.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A listing from a bulk-search row that is already in the catalogue.
 *
 * <p>Returned with each row so the "Added as …" marker on a card survives a
 * page reload. Held only in the browser, it disappeared the moment the user
 * refreshed and they could not tell which cards they had already saved.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavedListing {

    private String marketplace;
    private String marketplaceItemId;
    private Long productId;
    private String productSku;
}
