package com.priceintel.backend.dto.response;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A candidate ASIN for a product, confirmed to exist on Amazon.
 *
 * <p>Every field except {@link #asin} comes from the marketplace, not from the
 * model that proposed it — so the user compares their product against the real
 * listing rather than against a description something generated.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsinSuggestion {

    private String asin;

    /** Amazon's own title for this listing. */
    private String title;

    private String brand;

    /** The current price, or null when nobody is offering the item. */
    private BigDecimal price;

    private String currency;

    /** Amazon's storefront page for this ASIN, so a person can check it themselves. */
    private String url;

    private boolean available;

    /**
     * Whether another product in this catalogue already claims this ASIN.
     *
     * <p>Saving it would fail the uniqueness rule, so the UI should say why
     * before the user tries — the alternative is a confusing error after they
     * have chosen.</p>
     */
    private boolean alreadyUsedByAnotherProduct;
}
