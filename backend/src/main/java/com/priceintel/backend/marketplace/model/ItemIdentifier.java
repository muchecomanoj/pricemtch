package com.priceintel.backend.marketplace.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A product code a marketplace states for a listing — ASIN, UPC, EAN, GTIN,
 * ISBN, MPN.
 *
 * <p>A list of typed pairs rather than a map, because a product legitimately
 * carries more than one code of the same type: Amazon returns several UPCs for
 * a title sold in multiple package variations. A map keyed by type would keep
 * whichever arrived last and silently drop the rest, which is the wrong answer
 * to "which barcode is on the box".</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemIdentifier {

    /** ASIN, UPC, EAN, GTIN, ISBN, MPN — the marketplace's own label, uppercased. */
    private String type;

    private String value;
}
