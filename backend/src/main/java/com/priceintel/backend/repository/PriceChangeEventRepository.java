package com.priceintel.backend.repository;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.priceintel.backend.entity.PriceChangeEvent;

public interface PriceChangeEventRepository extends JpaRepository<PriceChangeEvent, Long> {

    Page<PriceChangeEvent> findByMarketplaceAndStorefrontAndMarketplaceItemIdOrderByObservedAtDesc(
            String marketplace, String storefront, String marketplaceItemId, Pageable pageable);

    /**
     * The recent-changes feed, optionally narrowed.
     *
     * <p>Every parameter takes a sentinel rather than null — {@code ""} for the
     * strings, the epoch for the date. Postgres cannot infer the type of a bare
     * null bound into a comparison and rejects the statement with "could not
     * determine data type of parameter"; comparing against a literal gives each
     * one a type. Call {@link #recent(String, String, Instant, Pageable)}.</p>
     */
    @Query("""
            SELECT e FROM PriceChangeEvent e
            WHERE (:tenantId = -1
                   OR EXISTS (
                       SELECT 1 FROM TenantTrackedItem t
                       WHERE t.tenantId = :tenantId
                         AND t.marketplace = e.marketplace
                         AND t.storefront = e.storefront
                         AND t.marketplaceItemId = e.marketplaceItemId)
                   OR EXISTS (
                       SELECT 1 FROM CompetitorListing c
                       WHERE c.tenantId = :tenantId
                         AND c.marketplace = e.marketplace
                         AND c.storefront = e.storefront
                         AND c.marketplaceItemId = e.marketplaceItemId))
              AND (:marketplace = '' OR e.marketplace = :marketplace)
              AND (:field = '' OR e.field = :field)
              AND e.observedAt >= :from
              AND (:search = '' OR LOWER(e.marketplaceItemId) LIKE :search
                   OR EXISTS (
                       SELECT 1 FROM MarketplaceItem m
                       WHERE m.marketplace = e.marketplace
                         AND m.storefront = e.storefront
                         AND m.marketplaceItemId = e.marketplaceItemId
                         AND (LOWER(m.title) LIKE :search
                              OR LOWER(m.identifiers) LIKE :search)))
            ORDER BY e.observedAt DESC
            """)
    Page<PriceChangeEvent> feed(@Param("tenantId") long tenantId,
                                @Param("marketplace") String marketplace,
                                @Param("field") String field,
                                @Param("from") Instant from,
                                @Param("search") String search,
                                Pageable pageable);

    /** Every company's changes: {@code ALL_COMPANIES} as the scope, for the platform owner only. */
    long ALL_COMPANIES = -1L;

    /** Applies the sentinels, so no caller has to remember them. */
    default Page<PriceChangeEvent> recent(long tenantScope, String marketplace, String field, Instant from,
                                          Pageable pageable) {
        return recent(tenantScope, marketplace, field, from, null, pageable);
    }

    /**
     * @param search matches the tracked item's title or any identifier it
     *               carries, and the item id itself — so a client can type
     *               "AirPods", an ASIN or a barcode and get the same answer.
     *               Matched in the database, before paging, because filtering a
     *               page after it has been cut gives the wrong page.
     */
    default Page<PriceChangeEvent> recent(long tenantScope, String marketplace, String field, Instant from,
                                          String search, Pageable pageable) {
        return feed(tenantScope, marketplace == null || marketplace.isBlank()
                        ? "" : marketplace.trim().toUpperCase(),
                field == null || field.isBlank() ? "" : field.trim().toUpperCase(),
                from != null ? from : Instant.EPOCH,
                search == null || search.isBlank()
                        ? "" : "%" + search.trim().toLowerCase() + "%",
                pageable);
    }
}
