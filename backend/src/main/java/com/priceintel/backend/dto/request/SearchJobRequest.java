package com.priceintel.backend.dto.request;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Start a product search across marketplaces. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchJobRequest {

    /** Markets to search (AMAZON, EBAY, WEB). Empty = all supported. */
    private List<String> markets;

    /** Max results per marketplace (default 5). */
    private Integer maxResults;

    /**
     * Skip the shared cache and re-fetch live from the marketplace.
     *
     * <p>Backs a "refresh now" button: by default a recent fetch of the same
     * query is reused, which is cheap but can be up to the cache TTL old. Set
     * this when the user explicitly wants today's price.</p>
     */
    private Boolean forceRefresh;

    /**
     * Where the buyer is, so delivery can be priced (§12.1 {@code destination}).
     *
     * <p>Optional. Absent, delivery stays unknown and every landed price built
     * without it is labelled as incomplete rather than presented as a total.</p>
     */
    @jakarta.validation.Valid
    private Destination destination;

    public boolean isForceRefresh() {
        return Boolean.TRUE.equals(forceRefresh);
    }
}
