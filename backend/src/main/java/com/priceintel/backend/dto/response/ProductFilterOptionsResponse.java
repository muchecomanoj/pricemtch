package com.priceintel.backend.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The choices to offer in the Products page filters.
 *
 * <p>Brands and categories are the values actually present in the caller's own
 * catalogue, so a dropdown never offers a filter that would return nothing.
 * Statuses are the fixed lifecycle enum. All three arrive in one call because
 * they are needed together, when the page loads.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductFilterOptionsResponse {

    /** Distinct brands in this catalogue, alphabetical. */
    private List<String> brands;

    /** Distinct category names in this catalogue, alphabetical. */
    private List<String> categories;

    /** Every product lifecycle status the API accepts. */
    private List<String> statuses;
}
