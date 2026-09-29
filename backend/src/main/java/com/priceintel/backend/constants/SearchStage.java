package com.priceintel.backend.constants;

/**
 * The ordered stages of the product search waterfall. Stages are tried in this
 * priority order; the first stage that returns results wins.
 */
public enum SearchStage {
    ASIN,
    GTIN,
    EAN,
    UPC,
    MPN,
    SKU,
    TITLE,
    URL,
    IMAGE,   // placeholder — visual matching is a future phase
    NONE     // used when nothing matched
}
