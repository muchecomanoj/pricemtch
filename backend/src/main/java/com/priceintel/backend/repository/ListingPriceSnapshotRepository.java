package com.priceintel.backend.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.priceintel.backend.entity.ListingPriceSnapshot;

@Repository
public interface ListingPriceSnapshotRepository extends JpaRepository<ListingPriceSnapshot, Long> {

    List<ListingPriceSnapshot> findByListingIdOrderByObservedAtAsc(Long listingId);

    List<ListingPriceSnapshot> findByListingIdAndObservedAtBetweenOrderByObservedAtAsc(
            Long listingId, Instant from, Instant to);

    /** All snapshots for a set of listings (for the product-level rolled-up history). */
    List<ListingPriceSnapshot> findByListingIdInAndObservedAtBetweenOrderByObservedAtAsc(
            List<Long> listingIds, Instant from, Instant to);

    // ---- shared history, keyed by the real-world listing ----
    //
    // Reading by (marketplace, storefront, item id) rather than by our row id
    // means every observation of that listing counts, whoever fetched it. One
    // dense series instead of one thin series per tenant.
    //
    // The storefront is part of the key. Without it the amazon.com and amazon.ca
    // copies of an ASIN shared one series that alternated between currencies.

    List<ListingPriceSnapshot> findByMarketplaceAndStorefrontAndMarketplaceItemIdOrderByObservedAtAsc(
            String marketplace, String storefront, String marketplaceItemId);

    List<ListingPriceSnapshot>
            findByMarketplaceAndStorefrontAndMarketplaceItemIdAndObservedAtBetweenOrderByObservedAtAsc(
                    String marketplace, String storefront, String marketplaceItemId,
                    Instant from, Instant to);

    /**
     * Snapshots for several item ids, in every marketplace and storefront.
     *
     * <p>Callers must keep only the (marketplace, storefront, item) keys they
     * asked about — an item id alone matches other shops' copies too.</p>
     */
    @Query("SELECT s FROM ListingPriceSnapshot s "
            + "WHERE s.marketplaceItemId IN :itemIds AND s.observedAt BETWEEN :from AND :to "
            + "ORDER BY s.observedAt ASC")
    List<ListingPriceSnapshot> findByItemIdsBetween(
            @Param("itemIds") List<String> itemIds,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /** Rows written before the item keys existed (one-time backfill). */
    List<ListingPriceSnapshot> findByMarketplaceItemIdIsNull();

    /**
     * The last price recorded for a marketplace item, whoever observed it.
     *
     * <p>Used to decide whether a new observation is worth storing. Scoped to
     * the item rather than to one tenant's row, because the price of an ASIN is
     * a single fact — if another client's search already recorded it, recording
     * it again adds a row and no information.</p>
     */
    java.util.Optional<ListingPriceSnapshot>
            findFirstByMarketplaceAndStorefrontAndMarketplaceItemIdOrderByObservedAtDesc(
                    String marketplace, String storefront, String marketplaceItemId);

    // ---- "does this listing have a chartable history?" ----
    //
    // Answered in one query for a whole product rather than per row, so the
    // Price History tab can hide listings whose chart would be empty without
    // paying an N+1. A snapshot with no price is not history: it would draw a
    // gap, so both queries require a usable figure.

    /**
     * Of the given real-world items, those with at least one priced observation.
     * Returned as {@code [marketplace, storefront, itemId]} because history is
     * keyed by all three — an id alone is unique neither across marketplaces
     * nor across one marketplace's storefronts.
     */
    @Query("SELECT DISTINCT s.marketplace, s.storefront, s.marketplaceItemId FROM ListingPriceSnapshot s "
            + "WHERE s.marketplaceItemId IN :itemIds "
            + "AND (s.landedPrice IS NOT NULL OR s.itemPrice IS NOT NULL)")
    List<Object[]> findItemKeysWithHistory(@Param("itemIds") List<String> itemIds);

    /**
     * Of the given listing rows, those with at least one priced observation.
     * The fallback for rows predating the item keys, which have per-row history
     * only.
     */
    @Query("SELECT DISTINCT s.listingId FROM ListingPriceSnapshot s "
            + "WHERE s.listingId IN :listingIds "
            + "AND (s.landedPrice IS NOT NULL OR s.itemPrice IS NOT NULL)")
    List<Long> findListingIdsWithHistory(@Param("listingIds") List<Long> listingIds);
}
