package com.priceintel.backend.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.priceintel.backend.entity.CompetitorListing;
import com.priceintel.backend.entity.MarketplaceSearchCache;
import com.priceintel.backend.repository.CompetitorListingRepository;
import com.priceintel.backend.repository.MarketplaceSearchCacheRepository;
import com.priceintel.backend.service.impl.CompetitorSearchCacheService;

/**
 * The shared-fetch rules: when a previous fetch may be reused for another
 * client, and when it must not be.
 */
class CompetitorSearchCacheServiceTest {

    private MarketplaceSearchCacheRepository cacheRepo;
    private CompetitorListingRepository listingRepo;
    private CompetitorSearchCacheService service;
    private Instant testStartedAt;

    private static final String ASIN = "B0DBHWQ58D";

    @BeforeEach
    void setUp() {
        testStartedAt = Instant.now();
        cacheRepo = mock(MarketplaceSearchCacheRepository.class);
        listingRepo = mock(CompetitorListingRepository.class);
        service = new CompetitorSearchCacheService(cacheRepo, listingRepo);
        ReflectionTestUtils.setField(service, "ttlHours", 48L);
        ReflectionTestUtils.setField(service, "enabled", true);
    }

    // ---------- hits ----------

    @Test
    void aRecentFetchIsReusedSoTheSecondClientPaysNoApiCall() {
        givenCacheEntry(hoursAgo(2), ASIN);
        when(listingRepo.findCachedByItems(eq("AMAZON"), anyList()))
                .thenReturn(List.of(listing(ASIN, hoursAgo(2))));

        var hit = service.lookup(Marketplace.AMAZON, ASIN);

        assertThat(hit).isPresent();
        assertThat(hit.get().listings()).hasSize(1);
        assertThat(hit.get().fetchedAt()).isEqualTo(hoursAgo(2));
    }

    @Test
    void theQueryIsNormalisedSoCasingDifferencesStillHit() {
        givenCacheEntry(hoursAgo(1), ASIN);
        when(listingRepo.findCachedByItems(eq("AMAZON"), anyList()))
                .thenReturn(List.of(listing(ASIN, hoursAgo(1))));

        assertThat(service.lookup(Marketplace.AMAZON, "  b0dbhwq58d ")).isPresent();
        verify(cacheRepo).findByMarketplaceAndQueryKey("AMAZON", ASIN.toLowerCase());
    }

    @Test
    void aHitIsCountedSoCacheValueIsMeasurable() {
        givenCacheEntry(hoursAgo(2), ASIN);
        when(listingRepo.findCachedByItems(eq("AMAZON"), anyList()))
                .thenReturn(List.of(listing(ASIN, hoursAgo(2))));

        service.lookup(Marketplace.AMAZON, ASIN);

        ArgumentCaptor<MarketplaceSearchCache> saved =
                ArgumentCaptor.forClass(MarketplaceSearchCache.class);
        verify(cacheRepo).save(saved.capture());
        assertThat(saved.getValue().getHitCount()).isEqualTo(1);
    }

    @Test
    void onlyTheFreshestCopyOfEachListingIsReturned() {
        givenCacheEntry(hoursAgo(2), ASIN);
        CompetitorListing newer = listing(ASIN, hoursAgo(2));
        CompetitorListing older = listing(ASIN, hoursAgo(30));
        // Repository returns newest-first; duplicates must collapse to one.
        when(listingRepo.findCachedByItems(eq("AMAZON"), anyList()))
                .thenReturn(List.of(newer, older));

        var hit = service.lookup(Marketplace.AMAZON, ASIN);

        assertThat(hit).isPresent();
        assertThat(hit.get().listings()).hasSize(1);
        assertThat(hit.get().listings().get(0).getLastFetchedAt()).isEqualTo(hoursAgo(2));
    }

    // ---------- storefronts ----------

    @Test
    void aCanadianEntryIsNeverAnsweredWithTheAmericanCopy() {
        // The same ASIN exists in both shops. The query found the Canadian one;
        // serving the US copy would present a US price as a Canadian competitor.
        givenRawCacheEntry(hoursAgo(2), CompetitorSearchCacheService.cacheKey("CA", ASIN));
        when(listingRepo.findCachedByItems(eq("AMAZON"), anyList()))
                .thenReturn(List.of(listing("US", ASIN, hoursAgo(1))));

        assertThat(service.lookup(Marketplace.AMAZON, ASIN)).isEmpty();
    }

    @Test
    void bothStorefrontCopiesAreKeptWhenBothWereFound() {
        givenRawCacheEntry(hoursAgo(2),
                CompetitorSearchCacheService.cacheKey("US", ASIN),
                CompetitorSearchCacheService.cacheKey("CA", ASIN));
        when(listingRepo.findCachedByItems(eq("AMAZON"), anyList()))
                .thenReturn(List.of(listing("US", ASIN, hoursAgo(1)),
                        listing("CA", ASIN, hoursAgo(1))));

        var hit = service.lookup(Marketplace.AMAZON, ASIN);

        assertThat(hit).isPresent();
        // Two listings, not one collapsed by item id.
        assertThat(hit.get().listings()).extracting(CompetitorListing::getStorefront)
                .containsExactlyInAnyOrder("US", "CA");
    }

    @Test
    void anEntryWrittenBeforeStorefrontsExistedIsAMiss() {
        // A bare id cannot say which shop it meant. Missing costs one API call;
        // guessing could serve the wrong country's price.
        givenRawCacheEntry(hoursAgo(2), ASIN);

        assertThat(service.lookup(Marketplace.AMAZON, ASIN)).isEmpty();
        verify(listingRepo, never()).findCachedByItems(anyString(), anyList());
    }

    // ---------- misses ----------

    @Test
    void anExpiredFetchIsNotReused() {
        givenCacheEntry(hoursAgo(49), ASIN);   // TTL is 48h

        assertThat(service.lookup(Marketplace.AMAZON, ASIN)).isEmpty();
        verify(listingRepo, never()).findCachedByItems(anyString(), anyList());
    }

    @Test
    void anUnknownQueryIsAMiss() {
        when(cacheRepo.findByMarketplaceAndQueryKey(anyString(), anyString()))
                .thenReturn(Optional.empty());

        assertThat(service.lookup(Marketplace.AMAZON, ASIN)).isEmpty();
    }

    @Test
    void anIndexPointingAtVanishedListingsIsAMissNotAnEmptyResult() {
        givenCacheEntry(hoursAgo(2), ASIN);
        when(listingRepo.findCachedByItems(eq("AMAZON"), anyList())).thenReturn(List.of());

        assertThat(service.lookup(Marketplace.AMAZON, ASIN)).isEmpty();
    }

    @Test
    void disablingTheCacheAlwaysGoesToTheMarketplace() {
        ReflectionTestUtils.setField(service, "enabled", false);
        givenCacheEntry(hoursAgo(1), ASIN);

        assertThat(service.lookup(Marketplace.AMAZON, ASIN)).isEmpty();
        verify(cacheRepo, never()).findByMarketplaceAndQueryKey(anyString(), anyString());
    }

    // ---------- store ----------

    @Test
    void aLiveFetchIsRecordedSoTheNextIdenticalSearchIsFree() {
        when(cacheRepo.findByMarketplaceAndQueryKey(anyString(), anyString()))
                .thenReturn(Optional.empty());

        service.store(Marketplace.AMAZON, ASIN, List.of("B01", "B02"));

        ArgumentCaptor<MarketplaceSearchCache> saved =
                ArgumentCaptor.forClass(MarketplaceSearchCache.class);
        verify(cacheRepo).save(saved.capture());
        assertThat(saved.getValue().getQueryKey()).isEqualTo(ASIN.toLowerCase());
        assertThat(saved.getValue().itemIdList()).containsExactly("B01", "B02");
        assertThat(saved.getValue().getFetchedAt()).isNotNull();
    }

    @Test
    void anEmptyResultSetIsNotCachedSoItRetriesNextTime() {
        service.store(Marketplace.AMAZON, ASIN, List.of());

        verify(cacheRepo, never()).save(any());
    }

    // ---------- helpers ----------

    /** A cache entry for US listings, written the way the search job now writes them. */
    private void givenCacheEntry(Instant fetchedAt, String... itemIds) {
        givenRawCacheEntry(fetchedAt, java.util.Arrays.stream(itemIds)
                .map(id -> CompetitorSearchCacheService.cacheKey("US", id)).toArray(String[]::new));
    }

    /** A cache entry holding exactly these strings, however they are formed. */
    private void givenRawCacheEntry(Instant fetchedAt, String... entries) {
        MarketplaceSearchCache entry = MarketplaceSearchCache.builder()
                .marketplace("AMAZON").queryKey(ASIN.toLowerCase()).fetchedAt(fetchedAt).build();
        entry.setItemIdList(List.of(entries));
        when(cacheRepo.findByMarketplaceAndQueryKey(anyString(), anyString()))
                .thenReturn(Optional.of(entry));
    }

    private CompetitorListing listing(String itemId, Instant fetchedAt) {
        return listing("US", itemId, fetchedAt);
    }

    private CompetitorListing listing(String storefront, String itemId, Instant fetchedAt) {
        return CompetitorListing.builder()
                .marketplace("AMAZON").storefront(storefront)
                .marketplaceItemId(itemId).lastFetchedAt(fetchedAt).build();
    }

    /**
     * Ages measured from a single instant captured when the test started.
     *
     * <p>Relative rather than absolute, deliberately: a hardcoded timestamp
     * passes on the day it is written and then silently starts failing once real
     * time drifts past the TTL, because the freshness rules under test are
     * defined against {@code Instant.now()}. Anchored once per test so repeated
     * calls return the same instant and equality assertions hold.</p>
     */
    private Instant hoursAgo(int hours) {
        return testStartedAt.minus(hours, ChronoUnit.HOURS);
    }
}
