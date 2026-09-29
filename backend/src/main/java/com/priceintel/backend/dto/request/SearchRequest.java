package com.priceintel.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A search query. The engine decides how to interpret it (ASIN, GTIN, SKU,
 * title, etc.) via the priority waterfall.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchRequest {

    @NotBlank(message = "Query is required")
    @Size(max = 500)
    private String query;
}
