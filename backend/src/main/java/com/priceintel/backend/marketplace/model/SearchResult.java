package com.priceintel.backend.marketplace.model;

import java.time.LocalDateTime;
import java.util.List;

import com.priceintel.backend.marketplace.Marketplace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Result of a marketplace search. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {
    private Marketplace marketplace;
    private String query;
    private int totalResults;
    private List<SearchResultItem> items;
    private LocalDateTime sourceTimestamp;
    private boolean mocked;

    /** Marketplace/region the returned items were actually priced in (e.g. GB). */
    private String countryCode;

    /** The provider's own marketplace id for that region (e.g. A1F83G8C2ARO7P). */
    private String marketplaceId;

    /** Regions attempted, in order — populated when a region fallback ran. */
    private List<String> regionsTried;

    /**
     * Regions that could not be queried at all, with the reason (e.g. the
     * connector is not authorized for that marketplace). Distinct from a region
     * that answered but had no priced listing.
     */
    private List<String> regionsUnavailable;

    /** Human-readable explanation when results were filtered or regions walked. */
    private String note;
}
