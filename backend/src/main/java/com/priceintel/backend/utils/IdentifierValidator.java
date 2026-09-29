package com.priceintel.backend.utils;

import java.util.Optional;

import com.priceintel.backend.constants.IdentifierType;

/**
 * Checks that an identifier is well-formed before it is stored (FR-PROD-002).
 *
 * <p>Barcodes carry their own check digit precisely so a mistyped one can be
 * caught without asking anybody. Storing an invalid code is worse than storing
 * none: the marketplace returns nothing, the search silently falls back to a
 * title match, and the resulting listings look like real competitors while
 * belonging to a different product entirely.</p>
 *
 * <p>Validation is structural only. A code can be perfectly formed and still not
 * exist — {@code B0DSAM6535} passes every rule here — so this narrows the
 * problem rather than solving it. Confirming an identifier exists needs a call
 * to the marketplace.</p>
 */
public final class IdentifierValidator {

    private IdentifierValidator() {
    }

    /**
     * @return the reason the value is invalid, or empty when it is acceptable
     */
    public static Optional<String> validate(IdentifierType type, String normalized) {
        if (type == null || normalized == null || normalized.isBlank()) {
            return Optional.empty();
        }
        return switch (type) {
            case ASIN -> validateAsin(normalized);
            case UPC -> validateGtin(normalized, 12, "UPC");
            case EAN -> validateGtin(normalized, 13, "EAN");
            case GTIN -> validateGtinAnyLength(normalized);
            // MPN is whatever the manufacturer prints — there is no format to
            // check, and inventing one would reject valid parts.
            case MPN, MARKETPLACE_ID -> Optional.empty();
        };
    }

    private static Optional<String> validateAsin(String value) {
        if (value.length() != 10) {
            return Optional.of("An ASIN is exactly 10 characters — '" + value
                    + "' has " + value.length() + ".");
        }
        if (!value.matches("[A-Z0-9]{10}")) {
            return Optional.of("An ASIN contains only letters and digits — '" + value + "' does not.");
        }
        return Optional.empty();
    }

    private static Optional<String> validateGtinAnyLength(String value) {
        int len = value.length();
        if (len != 8 && len != 12 && len != 13 && len != 14) {
            return Optional.of("A GTIN is 8, 12, 13 or 14 digits — '" + value
                    + "' has " + len + ".");
        }
        return validateGtin(value, len, "GTIN");
    }

    private static Optional<String> validateGtin(String value, int expectedLength, String label) {
        if (value.length() != expectedLength) {
            return Optional.of("A " + label + " is " + expectedLength + " digits — '" + value
                    + "' has " + value.length() + ".");
        }
        if (!value.matches("\\d+")) {
            return Optional.of("A " + label + " is digits only — '" + value + "' is not.");
        }
        if (!checkDigitMatches(value)) {
            return Optional.of("'" + value + "' is not a valid " + label
                    + " — its check digit does not match. Check for a typo.");
        }
        return Optional.empty();
    }

    /**
     * The GS1 mod-10 check digit, shared by UPC, EAN and every GTIN length.
     *
     * <p>Weights alternate 3 and 1 from the rightmost data digit leftwards, so
     * the same routine works whatever the length.</p>
     */
    private static boolean checkDigitMatches(String value) {
        int lastIndex = value.length() - 1;
        int sum = 0;
        int weight = 3;
        for (int i = lastIndex - 1; i >= 0; i--) {
            sum += (value.charAt(i) - '0') * weight;
            weight = weight == 3 ? 1 : 3;
        }
        int expected = (10 - (sum % 10)) % 10;
        return expected == (value.charAt(lastIndex) - '0');
    }
}
