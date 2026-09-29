package com.priceintel.backend.utils;

import com.priceintel.backend.constants.IdentifierType;

/**
 * Normalizes external identifiers so the same code entered in different forms
 * (spaces, hyphens, lower-case) resolves to one canonical value for matching.
 * The ORIGINAL value is always kept separately on the entity.
 */
public final class IdentifierNormalizer {

    private IdentifierNormalizer() {
    }

    public static String normalize(IdentifierType type, String original) {
        if (original == null) {
            return null;
        }
        // Upper-case and strip separators (spaces and hyphens) for every type, so
        // the same code entered in different forms normalizes identically. This
        // keeps storage and search consistent.
        return original.trim().toUpperCase().replaceAll("[\\s-]", "");
    }
}
