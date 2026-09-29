package com.priceintel.backend.constants;

/**
 * The condition a product is sold in.
 *
 * <p>Condition belongs on the product because it changes what a fair price is:
 * a used unit should not be compared against new competitor listings, and the
 * same margin on a refurbished item means something different. Competitor
 * listings already carry their own condition — this is our side of that
 * comparison.</p>
 */
public enum ProductCondition {
    NEW,
    USED,
    REFURBISHED
}
