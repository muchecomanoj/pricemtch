package com.priceintel.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.priceintel.backend.constants.MatchStatus;
import com.priceintel.backend.entity.CompetitorListing;

@Repository
public interface CompetitorListingRepository extends JpaRepository<CompetitorListing, Long> {

    List<CompetitorListing> findByProductIdOrderByCreatedAtDesc(Long productId);

    /**
     * The listings a run returned — those seen at or after it started.
     *
     * <p>Uses last_seen_at rather than source_timestamp so a run served from the
     * shared cache still reports what it found.</p>
     */
    List<CompetitorListing> findByProductIdAndLastSeenAtGreaterThanEqualOrderByLastPriceAsc(
            Long productId, java.time.Instant since);

    /**
     * One product's copy of one listing in one storefront.
     *
     * <p>The storefront is required. Without it the US and Canadian versions of
     * an ASIN were the same row, so attaching the Canadian one returned the
     * American one as "already attached".</p>
     */
    Optional<CompetitorListing> findByProductIdAndMarketplaceAndStorefrontAndMarketplaceItemId(
            Long productId, String marketplace, String storefront, String marketplaceItemId);

    long countByProductId(Long productId);

    /** Listings the Competitors tab shows: everything except rejected. */
    long countByProductIdAndMatchStatusNot(
            Long productId, com.priceintel.backend.constants.MatchStatus matchStatus);

    /**
     * Listings that could actually drive an alert or a statistic.
     *
     * <p>Mirrors exactly what {@code marketPrices()} reads: not rejected
     * <em>and</em> carrying a price. Both halves matter and each was got wrong
     * once. Unreviewed candidates count — the engine evaluates them. A listing
     * with no price does not, however well matched: found on a marketplace but
     * never successfully priced, it is a row with nothing to compare.</p>
     */
    long countByProductIdAndMatchStatusNotAndLastPriceIsNotNull(
            Long productId, com.priceintel.backend.constants.MatchStatus matchStatus);

    /**
     * The same count for a whole page of products, as {@code [productId, count]}.
     *
     * <p>One query for the page rather than one per row: a 100-product list would
     * otherwise issue 100 counts to render a single column.</p>
     *
     * <p>Products with no listings at all are simply absent from the result —
     * there is no row to group — so callers must default them to zero rather
     * than to null.</p>
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT l.productId, COUNT(l), COUNT(l.lastPrice) FROM CompetitorListing l
            WHERE l.productId IN :productIds AND l.matchStatus <> :excluded
            GROUP BY l.productId
            """)
    java.util.List<Object[]> countActiveByProductIds(
            @org.springframework.data.repository.query.Param("productIds")
            java.util.Collection<Long> productIds,
            @org.springframework.data.repository.query.Param("excluded")
            com.priceintel.backend.constants.MatchStatus excluded);

    /** Listings that have not been scored yet (used by the one-time backfill). */
    List<CompetitorListing> findByMatchScoreIsNull();

    /**
     * The most recently fetched copy of a real-world listing, whichever product
     * it was captured for. This is the read side of the shared cache: the price
     * of an ASIN is a fact about the marketplace, so it can be reused across
     * products and tenants rather than re-fetched per client.
     */
    @Query("SELECT c FROM CompetitorListing c "
            + "WHERE c.marketplace = :marketplace AND c.storefront = :storefront "
            + "AND c.marketplaceItemId = :itemId "
            + "AND c.lastFetchedAt IS NOT NULL "
            + "ORDER BY c.lastFetchedAt DESC")
    List<CompetitorListing> findFreshestByItem(
            @Param("marketplace") String marketplace,
            @Param("storefront") String storefront,
            @Param("itemId") String itemId,
            Pageable pageable);

    /**
     * Every cached copy for a set of item ids, in any storefront.
     *
     * <p>Callers must narrow the result to the storefront they want. Item ids are
     * shared between storefronts, and returning a US copy for a Canadian cache
     * entry is exactly the confusion the storefront key exists to end.</p>
     */
    @Query("SELECT c FROM CompetitorListing c "
            + "WHERE c.marketplace = :marketplace AND c.marketplaceItemId IN :itemIds "
            + "AND c.lastFetchedAt IS NOT NULL "
            + "ORDER BY c.lastFetchedAt DESC")
    List<CompetitorListing> findCachedByItems(
            @Param("marketplace") String marketplace,
            @Param("itemIds") List<String> itemIds);

    /** Rows captured before fetch-time tracking existed (one-time backfill). */
    List<CompetitorListing> findByLastFetchedAtIsNull();

    /**
     * Tenant-wide competitor listings for the Competitor Listings page, with
     * optional filters. Any null filter is ignored. {@code search} matches
     * seller or title (case-insensitive) and must be passed lower-cased.
     */
    @Query("SELECT c FROM CompetitorListing c WHERE "
            + "(:tenantId IS NULL OR c.tenantId = :tenantId) AND "
            + "(:marketplace IS NULL OR c.marketplace = :marketplace) AND "
            + "(:condition IS NULL OR c.condition = :condition) AND "
            + "(:matchStatus IS NULL OR c.matchStatus = :matchStatus) AND "
            + "(CAST(:search AS string) IS NULL "
            + "   OR LOWER(c.seller) LIKE CONCAT('%', CAST(:search AS string), '%') "
            + "   OR LOWER(c.title) LIKE CONCAT('%', CAST(:search AS string), '%'))")
    Page<CompetitorListing> search(
            @Param("tenantId") Long tenantId,
            @Param("marketplace") String marketplace,
            @Param("condition") String condition,
            @Param("matchStatus") MatchStatus matchStatus,
            @Param("search") String search,
            Pageable pageable);

    /**
     * Listings awaiting review for the Match Review page: filter by status
     * (defaults to CANDIDATE at the service layer) and optionally by product.
     *
     * <p>{@code tenantId} null means the platform owner, who sees every tenant.
     * For anyone else it is mandatory — a reviewer must never be shown another
     * client's candidates.</p>
     */
    @Query("SELECT c FROM CompetitorListing c WHERE c.matchStatus = :status AND "
            + "(:tenantId IS NULL OR c.tenantId = :tenantId) AND "
            + "(:productId IS NULL OR c.productId = :productId)")
    Page<CompetitorListing> findForReview(
            @Param("status") MatchStatus status,
            @Param("tenantId") Long tenantId,
            @Param("productId") Long productId,
            Pageable pageable);

    /** Rows predating the tenant column (one-time backfill). */
    List<CompetitorListing> findByTenantIdIsNull();

    /** Every listing owned by one tenant — used by exports and reports. */
    List<CompetitorListing> findByTenantId(Long tenantId);
}
