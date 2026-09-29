package com.priceintel.backend.product;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.priceintel.backend.constants.IdentifierType;
import com.priceintel.backend.utils.IdentifierValidator;

/**
 * A check-digit routine that is subtly wrong is worse than none: it rejects real
 * barcodes, and the person typing one has no way to tell who is mistaken. These
 * pin the algorithm against known-good codes.
 */
class IdentifierValidatorTest {

    private boolean accepts(IdentifierType type, String value) {
        return IdentifierValidator.validate(type, value).isEmpty();
    }

    private String reason(IdentifierType type, String value) {
        return IdentifierValidator.validate(type, value).orElse("");
    }

    // ---------- UPC ----------

    @Test
    @DisplayName("a valid 12-digit UPC is accepted")
    void realUpcAccepted() {
        // The GS1 worked example, so this pins the algorithm to a published
        // value rather than to one of our own records.
        assertThat(accepts(IdentifierType.UPC, "036000291452")).isTrue();
    }

    @Test
    @DisplayName("a single mistyped digit fails the check digit")
    void mistypedUpcRejected() {
        // The same code with one digit changed — length and shape still valid.
        assertThat(accepts(IdentifierType.UPC, "036000291453")).isFalse();
        assertThat(reason(IdentifierType.UPC, "036000291453")).contains("check digit");
    }

    @Test
    @DisplayName("the invented barcodes already in the catalogue are caught")
    void fabricatedBarcodesRejected() {
        // Every UPC/EAN in the seeded catalogue fails its check digit. They look
        // plausible, which is exactly why they went unnoticed and why the
        // marketplace returned nothing for them.
        assertThat(accepts(IdentifierType.UPC, "194253392744")).isFalse();
        assertThat(accepts(IdentifierType.EAN, "8806099987654")).isFalse();
    }

    @Test
    @DisplayName("a UPC of the wrong length is rejected before the check digit")
    void shortUpcRejected() {
        assertThat(reason(IdentifierType.UPC, "19425339274")).contains("12 digits");
    }

    // ---------- EAN / GTIN ----------

    @Test
    @DisplayName("a valid 13-digit EAN is accepted")
    void realEanAccepted() {
        assertThat(accepts(IdentifierType.EAN, "4006381333931")).isTrue();
    }

    @Test
    @DisplayName("GTIN accepts every valid length")
    void gtinLengths() {
        assertThat(accepts(IdentifierType.GTIN, "4006381333931")).isTrue();   // 13
        assertThat(accepts(IdentifierType.GTIN, "036000291452")).isTrue();    // 12
        assertThat(reason(IdentifierType.GTIN, "1942533927")).contains("8, 12, 13 or 14");
    }

    // ---------- ASIN ----------

    @Test
    @DisplayName("a well-formed ASIN is accepted")
    void asinAccepted() {
        assertThat(accepts(IdentifierType.ASIN, "B0BDHWDR12")).isTrue();
        assertThat(accepts(IdentifierType.ASIN, "B004YAVRTW")).isTrue();
    }

    @Test
    @DisplayName("the nine-character ASIN that got into the catalogue is now rejected")
    void shortAsinRejected() {
        // B08BOS600 was stored against a real product — nothing checked its length.
        assertThat(accepts(IdentifierType.ASIN, "B08BOS600")).isFalse();
        assertThat(reason(IdentifierType.ASIN, "B08BOS600")).contains("10 characters");
    }

    @Test
    @DisplayName("a fabricated but well-formed ASIN still passes — structure is not existence")
    void fabricatedAsinPasses() {
        // B0DSAM6535 does not exist on Amazon. No format rule can know that;
        // catching it needs a marketplace call. Pinned so the limit is explicit.
        assertThat(accepts(IdentifierType.ASIN, "B0DSAM6535")).isTrue();
    }

    // ---------- types without a format ----------

    @Test
    @DisplayName("MPN is never rejected — manufacturers use any format they like")
    void mpnUnvalidated() {
        assertThat(accepts(IdentifierType.MPN, "910-006559")).isTrue();
        assertThat(accepts(IdentifierType.MPN, "RS76CG8113SL")).isTrue();
    }

    @Test
    @DisplayName("null and blank are left to the required-field rules")
    void blanksIgnored() {
        assertThat(accepts(IdentifierType.ASIN, null)).isTrue();
        assertThat(accepts(IdentifierType.ASIN, "   ")).isTrue();
    }
}
