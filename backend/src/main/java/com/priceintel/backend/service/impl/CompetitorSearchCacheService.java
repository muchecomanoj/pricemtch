package com.priceintel.backend.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.priceintel.backend.entity.CompetitorListing;
import com.priceintel.backend.entity.MarketplaceSearchCache;
import com.priceintel.backend.marketplace.Marketplace;
import com.priceintel.backend.repository.CompetitorListingRepository;
import com.priceintel.backend.repository.MarketplaceSearchCacheRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Shared cache of marketplace search results.
 *
 * <p>Two clients tracking the same product should cost one API call, not two.
 * What a marketplace returns for a given query is a fact about the marketplace,
 * so it is cached globally and reused; each client's private judgments (match
 * decisions, costs, margins) stay on their own competitor listings.</p>
 *
 * <p>Entries expire after {@code app.competitor-cache.ttl-hours} so a stale
 * price never silently drives a repricing decision, and any search can bypass
 * the cache entirely by asking for a refresh.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompetitorSearchCacheService {

    private final MarketplaceSearchCacheRepository cacheRepo;
    private final CompetitorListingRepository listingRepo;

    /** How long a cached result stays usable. Default 48h. */
    @Value("${app.competitor-cache.ttl-hours:48}")
    private long ttlHours;

    @Value("${app.competitor-cache.enabled:true}")
    private boolean enabled;

    /** A cache hit: the listings previously fetched for this query. */
    public record CachedSearch(List<CompetitorListing> listings, Instant fetchedAt) {
    }

    public Duration ttl() {
        return Duration.ofHours(ttlHours);
    }

    /**
     * Looks for a usable previous fetch of this query.
     *
     * @return the cached listings, or empty when absent, expired, or disabled
     */
    @Transactional
    public Optional<CachedSearch> lookup(Marketplace marketplace, String query) {
        return lookup(marketplace, query, false);
    }

    /**
     * Looks up a previous fetch, accepting it only if it is younger than
     * {@code maxAge}.
     *
     * <p>This is what lets a scheduled monitor promise a refresh rate it
     * actually keeps. The global TTL is a ceiling for interactive searches, but
     * an hourly monitor that accepted 48-hour-old data would report fresh
     * results while never once calling the marketplace. Passing its own interval
     * as the bound means a monitor reuses a fetch only if that fetch is newer
     * than the cadence it advertises — and two monitors on the same cadence
     * still share one call.</p>
     */
    @Transactional
    public Optional<CachedSearch> lookupWithin(Marketplace marketplace, String query, Duration maxAge) {
        if (maxAge == null) {
            return lookup(marketplace, query, false);
        }
        Optional<CachedSearch> hit = lookup(marketplace, query, true);
        return hit.filter(c -> c.fetchedAt() != null
                && c.fetchedAt().isAfter(Instant.now().minus(maxAge)));
    }

    /**
     * Looks up previously fetched results for a query.
     *
     * @param ignoreTtl show what exists regardless of age. Used when opening a
     *                  page: research already done for this product is worth
     *                  displaying even if it is old, because the alternative is
     *                  an empty screen. The row carries its true fetch time, so
     *                  the user can see the age and refresh if they want. TTL
     *                  still governs whether a <i>search</i> may skip the API.
     */
    @Transactional
    public Optional<CachedSearch> lookup(Marketplace marketplace, String query, boolean ignoreTtl) {
        if (!enabled) {
            return Optional.empty();
        }
        String key = MarketplaceSearchCache.normalizeKey(query);
        Optional<MarketplaceSearchCache> found =
                cacheRepo.findByMarketplaceAndQueryKey(marketplace.name(), key);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        MarketplaceSearchCache entry = found.get();

        if (!ignoreTtl && (entry.getFetchedAt() == null
                || entry.getFetchedAt().isBefore(Instant.now().minus(ttl())))) {
            log.debug("Cache expired for {} '{}' (fetched {})", marketplace, key, entry.getFetchedAt());
            return Optional.empty();
        }

        List<String> keys = entry.itemIdList();
        if (keys.isEmpty()) {
            return Optional.empty();
        }
        // Each entry names a storefront and an item. An entry without the
        // separator predates storefronts and cannot say which shop it meant, so
        // it is not trusted: a miss costs one API call, a wrong hit serves a US
        // price as a Canadian one.
        java.util.Set<String> wanted = new java.util.LinkedHashSet<>();
        List<String> itemIds = new java.util.ArrayList<>();
        for (String cached : keys) {
            int sep = cached.indexOf(KEY_SEPARATOR);
            if (sep <= 0) {
                return Optional.empty();
            }
            wanted.add(cached);
            itemIds.add(cached.substring(sep + 1));
        }

        // Keep only the freshest copy of each listing — several products may have
        // captured the same one at different times. Other storefronts' copies of
        // the same item id are dropped here.
        Map<String, CompetitorListing> newestPerItem = new LinkedHashMap<>();
        for (CompetitorListing l : listingRepo.findCachedByItems(marketplace.name(), itemIds)) {
            String listingKey = cacheKey(l.getStorefront(), l.getMarketplaceItemId());
            if (wanted.contains(listingKey)) {
                newestPerItem.putIfAbsent(listingKey, l);
            }
        }
        if (newestPerItem.isEmpty()) {
            // The index survived but the listings did not — treat as a miss.
            return Optional.empty();
        }

        entry.setHitCount(entry.getHitCount() + 1);
        cacheRepo.save(entry);
        log.info("Cache HIT for {} '{}' — {} listing(s), fetched {} (hit #{})",
                marketplace, key, newestPerItem.size(), entry.getFetchedAt(), entry.getHitCount());
        return Optional.of(new CachedSearch(List.copyOf(newestPerItem.values()), entry.getFetchedAt()));
    }

    /** Separates storefront from item id in a cache entry. Never occurs in either. */
    private static final char KEY_SEPARATOR = ':';

    /**
     * How one listing is written into a cache entry: {@code CA:B08L5NP6NG}.
     *
     * <p>Neither half contains a colon — storefronts are two letters, Amazon ids
     * are alphanumeric, eBay ids use digits and pipes.</p>
     */
    public static String cacheKey(String storefront, String itemId) {
        return storefront + KEY_SEPARATOR + itemId;
    }

    /**
     * Records what a live fetch returned, so the next identical search is free.
     *
     * @param itemIds entries built with {@link #cacheKey}, not bare item ids
     */
    @Transactional
    public void store(Marketplace marketplace, String query, List<String> itemIds) {
        if (!enabled || itemIds.isEmpty()) {
            return;
        }
        String key = MarketplaceSearchCache.normalizeKey(query);
        MarketplaceSearchCache entry = cacheRepo
                .findByMarketplaceAndQueryKey(marketplace.name(), key)
                .orElseGet(() -> MarketplaceSearchCache.builder()
                        .marketplace(marketplace.name()).queryKey(key).build());
        entry.setItemIdList(itemIds);
        entry.setFetchedAt(Instant.now());
        cacheRepo.save(entry);
        log.debug("Cached {} result(s) for {} '{}'", itemIds.size(), marketplace, key);
    }

    /**
     * The freshest known price for one real-world listing, regardless of which
     * product captured it. Used to fill a gap without spending an API call.
     */
    @Transactional(readOnly = true)
    public Optional<CompetitorListing> freshestFor(Marketplace marketplace, String storefront,
                                                   String itemId) {
        return listingRepo.findFreshestByItem(marketplace.name(), storefront, itemId,
                        PageRequest.of(0, 1))
                .stream().findFirst()
                .filter(l -> l.getLastFetchedAt() != null
                        && l.getLastFetchedAt().isAfter(Instant.now().minus(ttl())));
    }
}
