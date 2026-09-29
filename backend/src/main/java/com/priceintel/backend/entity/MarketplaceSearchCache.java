package com.priceintel.backend.entity;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Remembers which marketplace listings a given search returned, so the same
 * search does not have to hit the marketplace API again.
 *
 * <p>Deliberately <b>global</b> — no tenant column. What Amazon returns for
 * "B0DBHWQ58D" is a fact about the marketplace, not about the client who asked,
 * so two clients tracking the same product share one fetch. Their private
 * judgments (match decisions, costs) stay on their own competitor listings.</p>
 *
 * <p>Only the item ids are stored; the listing detail lives in
 * {@code competitor_listings} and is re-read from there on a cache hit.</p>
 */
@Entity
@Table(name = "marketplace_search_cache",
        uniqueConstraints = @UniqueConstraint(name = "uk_search_cache_query",
                columnNames = {"marketplace", "query_key"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketplaceSearchCache extends BaseEntity {

    @Column(nullable = false, length = 20)
    private String marketplace;

    /**
     * The normalised search term. Identifier-based queries (an ASIN) normalise
     * to the same key for every client, which is what makes sharing work.
     */
    @Column(name = "query_key", nullable = false, length = 500)
    private String queryKey;

    /** Marketplace item ids this query returned, comma-separated. */
    @Column(name = "item_ids", columnDefinition = "TEXT")
    private String itemIds;

    /** When these results were actually retrieved from the marketplace. */
    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    /** How many times this cached entry has been served instead of an API call. */
    @Builder.Default
    @Column(name = "hit_count", nullable = false)
    private int hitCount = 0;

    public List<String> itemIdList() {
        if (itemIds == null || itemIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(itemIds.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public void setItemIdList(List<String> ids) {
        this.itemIds = String.join(",", ids);
    }

    /** Normalises a raw query so the same product yields the same key. */
    public static String normalizeKey(String query) {
        return query == null ? "" : query.trim().toLowerCase().replaceAll("\\s+", " ");
    }
}
