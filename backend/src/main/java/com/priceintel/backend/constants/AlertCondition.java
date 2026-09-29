package com.priceintel.backend.constants;

/** Alert rule trigger conditions (FR-ALERT-001). */
public enum AlertCondition {
    PRICE_DROP,
    PRICE_INCREASE,
    COMPETITOR_CHANGE,
    NEW_SELLER,
    OUT_OF_STOCK,
    MARGIN_BREACH,
    PRICE_GAP,
    DATA_STALE
}
