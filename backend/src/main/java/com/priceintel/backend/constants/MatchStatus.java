package com.priceintel.backend.constants;

/** Review state of a competitor listing matched to a product. */
public enum MatchStatus {
    CANDIDATE,   // found by search, awaiting review
    MATCHED,     // confirmed the same product
    EQUIVALENT,  // comparable (e.g. compatible/substitute)
    REJECTED     // not the same product
}
