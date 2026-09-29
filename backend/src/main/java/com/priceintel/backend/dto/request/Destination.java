package com.priceintel.backend.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Where the buyer is, so delivery can be priced (FR §12.1, §13.1, UAT-04).
 *
 * <p>There is no such thing as "the" shipping cost for a listing — only the cost
 * to somewhere. Without a destination a marketplace either omits delivery or
 * quotes it for a default location, and a landed price built on that is a guess
 * wearing the clothes of a fact.</p>
 *
 * <p>Both fields are optional and useful separately: a country alone is enough
 * for a marketplace that ships flat-rate nationally, while a postcode narrows it
 * to the buyer's actual zone. Supplying neither is allowed and simply means
 * delivery stays unknown — which is then reported as unknown rather than
 * silently treated as free.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Destination {

    /** ISO 3166-1 alpha-2, e.g. {@code US}, {@code IN}, {@code GB}. */
    @Pattern(regexp = "^$|^[A-Za-z]{2}$", message = "Country must be a two-letter ISO code")
    private String country;

    /** Postal or ZIP code as the destination country writes it. */
    @Size(max = 20, message = "Postal code must be 20 characters or fewer")
    private String postalCode;

    public boolean isEmpty() {
        return blank(country) && blank(postalCode);
    }

    public String normalisedCountry() {
        return blank(country) ? null : country.trim().toUpperCase();
    }

    public String normalisedPostalCode() {
        return blank(postalCode) ? null : postalCode.trim();
    }

    private boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
