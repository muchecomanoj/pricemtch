package com.priceintel.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketplaceSearchRequest {

    @NotBlank(message = "Query is required")
    @Size(max = 500)
    private String query;

    @Positive(message = "maxResults must be positive")
    private Integer maxResults;

    /**
     * Hide listings that have no price (default true). A product with no price
     * cannot be compared or used for profitability, so it is noise in the UI.
     * For Amazon this also drives the region hunt: marketplaces are tried in
     * turn until one returns priced listings. Set false to see raw results.
     */
    private Boolean requirePrice;

    /**
     * What {@code query} is: {@code ASIN}, {@code UPC}, {@code EAN}, {@code GTIN},
     * or null/{@code KEYWORD} for free text.
     *
     * <p>A barcode searched as keywords is matched against titles and rarely
     * finds the product; the marketplaces have a separate identifier lookup that
     * returns the exact item. Telling them which one they were handed is the
     * difference between an exact hit and a page of near-misses.</p>
     *
     * <p>An identifier a marketplace cannot look up directly — MPN anywhere, or
     * a barcode on a connector without an identifier endpoint — falls back to a
     * keyword search rather than failing, and the result says so.</p>
     */
    private String identifierType;

    /**
     * Where the buyer is, so delivery can be priced. Null leaves shipping
     * unknown — which is reported as unknown, not as free.
     */
    @jakarta.validation.Valid
    private Destination destination;

    /**
     * Which storefront to search — {@code US}, {@code GB}, {@code IN} …
     *
     * <p>Distinct from {@link #destination}, which is where the parcel goes.
     * This chooses whose catalogue and prices to read.</p>
     *
     * <p>Named explicitly, the connectors search <em>only</em> that storefront
     * and report honestly when they cannot reach it. Left null, they fall back
     * across their configured regions as before. Choosing a region and silently
     * receiving another one's prices is the one outcome to avoid.</p>
     */
    private String region;

    /** True when the query should be treated as an identifier, not free text. */
    public boolean hasIdentifierType() {
        return identifierType != null && !identifierType.isBlank()
                && !"KEYWORD".equalsIgnoreCase(identifierType.trim());
    }

    /** The identifier type, upper-cased, or null for a keyword search. */
    public String normalisedIdentifierType() {
        return hasIdentifierType() ? identifierType.trim().toUpperCase() : null;
    }

    /** True unless the caller explicitly opted out. */
    public boolean isPriceRequired() {
        return !Boolean.FALSE.equals(requirePrice);
    }
}
