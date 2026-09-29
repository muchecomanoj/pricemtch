package com.priceintel.backend.product;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.priceintel.backend.dto.request.Destination;

/**
 * The destination, and what it means when we do not have one.
 *
 * <p>UAT-04. Shipping is unknown on 192 of 200 priced listings, and the old
 * behaviour computed a landed price as though unknown meant free — understating
 * every such competitor by exactly their postage, always in the same direction.</p>
 */
class DestinationAndLandedPriceTest {

    @Test
    @DisplayName("a country and postcode normalise for the marketplace")
    void normalises() {
        Destination d = Destination.builder().country(" us ").postalCode(" 10001 ").build();

        assertThat(d.normalisedCountry()).isEqualTo("US");
        assertThat(d.normalisedPostalCode()).isEqualTo("10001");
        assertThat(d.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("no destination is a valid state, not an error")
    void emptyIsAllowed() {
        assertThat(new Destination().isEmpty()).isTrue();
        assertThat(Destination.builder().country("  ").postalCode("").build().isEmpty()).isTrue();

        // Absent fields read as null rather than empty strings, so a caller
        // cannot accidentally send "country=" to a marketplace.
        assertThat(new Destination().normalisedCountry()).isNull();
        assertThat(new Destination().normalisedPostalCode()).isNull();
    }

    @Test
    @DisplayName("a country alone is usable; a postcode alone is not")
    void countryIsTheMinimum() {
        Destination countryOnly = Destination.builder().country("GB").build();
        assertThat(countryOnly.isEmpty()).isFalse();
        assertThat(countryOnly.normalisedCountry()).isEqualTo("GB");
        assertThat(countryOnly.normalisedPostalCode()).isNull();

        // A postcode without a country is ambiguous — 10001 is Manhattan in the
        // US and a Copenhagen suburb in Denmark. The object still holds it; the
        // eBay client is what declines to send it alone.
        Destination zipOnly = Destination.builder().postalCode("10001").build();
        assertThat(zipOnly.normalisedCountry()).isNull();
        assertThat(zipOnly.normalisedPostalCode()).isEqualTo("10001");
    }
}
