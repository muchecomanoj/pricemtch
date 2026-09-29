package com.priceintel.backend.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.priceintel.backend.entity.MarketplaceItem;

public interface MarketplaceItemRepository extends JpaRepository<MarketplaceItem, Long> {

    Optional<MarketplaceItem> findByMarketplaceAndStorefrontAndMarketplaceItemId(
            String marketplace, String storefront, String marketplaceItemId);

    /**
     * Tracked items for a set of item ids, in any storefront.
     *
     * <p>One query for a whole page of changes. Callers match on the full key —
     * marketplace, storefront and item id — because an id alone is shared
     * between storefronts.</p>
     */
    java.util.List<MarketplaceItem> findByMarketplaceItemIdIn(
            java.util.Collection<String> marketplaceItemIds);

    /**
     * The most recently seen storefront copy of an item, for callers that name
     * an item without a storefront. The row returned states its own storefront,
     * so nothing downstream has to guess which one was chosen.
     */
    Optional<MarketplaceItem> findFirstByMarketplaceAndMarketplaceItemIdOrderByLastSeenAtDesc(
            String marketplace, String marketplaceItemId);

    /**
     * Filtered list for the tracked-items page.
     *
     * <p>Both parameters take a sentinel rather than null — {@code ""} for no
     * marketplace, {@code "%"} for no search. Postgres cannot infer the type of
     * a bare null bound inside {@code LOWER}/{@code CONCAT} and rejects the
     * statement with "function lower(bytea) does not exist"; comparing against a
     * string literal gives every parameter a type. Use
     * {@link #searchItems(String, String, Pageable)}, which builds them.</p>
     */
    /**
     * Limited to listings the company searched or attached — see
     * PriceChangeEventRepository.feed for why. {@code tenantId = -1} is the
     * platform owner, who sees every listing.
     */
    @Query("""
            SELECT m FROM MarketplaceItem m
            WHERE (:tenantId = -1
                   OR EXISTS (
                       SELECT 1 FROM TenantTrackedItem t
                       WHERE t.tenantId = :tenantId
                         AND t.marketplace = m.marketplace
                         AND t.storefront = m.storefront
                         AND t.marketplaceItemId = m.marketplaceItemId)
                   OR EXISTS (
                       SELECT 1 FROM CompetitorListing c
                       WHERE c.tenantId = :tenantId
                         AND c.marketplace = m.marketplace
                         AND c.storefront = m.storefront
                         AND c.marketplaceItemId = m.marketplaceItemId))
              AND (:marketplace = '' OR m.marketplace = :marketplace)
              AND (LOWER(m.title) LIKE :pattern OR LOWER(m.marketplaceItemId) LIKE :pattern)
            """)
    Page<MarketplaceItem> search(@Param("tenantId") long tenantId,
                                 @Param("marketplace") String marketplace,
                                 @Param("pattern") String pattern,
                                 Pageable pageable);

    /** Applies the sentinels, so no caller has to remember them. */
    default Page<MarketplaceItem> searchItems(long tenantScope, String marketplace, String search,
                                              Pageable pageable) {
        String mp = marketplace == null || marketplace.isBlank()
                ? "" : marketplace.trim().toUpperCase();
        String pattern = search == null || search.isBlank()
                ? "%" : "%" + search.trim().toLowerCase() + "%";
        return search(tenantScope, mp, pattern, pageable);
    }
}
