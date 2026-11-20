package com.priceintel.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;

import com.priceintel.backend.constants.MatchStatus;
import com.priceintel.backend.entity.CompetitorListing;
import com.priceintel.backend.entity.ListingPriceSnapshot;
import com.priceintel.backend.exception.MarketplaceApiException;
import com.priceintel.backend.exception.ResourceNotFoundException;
import com.priceintel.backend.marketplace.Marketplace;
import com.priceintel.backend.marketplace.model.PriceSnapshot;
import com.priceintel.backend.repository.CompetitorListingRepository;
import com.priceintel.backend.repository.ListingPriceSnapshotRepository;
import com.priceintel.backend.service.MarketplaceService;
import com.priceintel.backend.service.impl.SearchPipelineService;

/**
 * Re-pricing the listings a monitor's search did not return.
 *
 * <p>A monitor searches and refreshes its top few results. One product had 42
 * attached listings of which 33 had gone unchecked for weeks, while the
 * Competitors tab showed their stale prices with no hint of their age. This
 * looks the rest up directly.</p>
 *
 * <p>Three things are worth protecting: a steady price writes no history, a
 * listing that has ended is an answer rather than an error, and one unreachable
 * marketplace does not end the pass.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ListingRecheckTest {

    @Mock CompetitorListingRepository listingRepo;
    @Mock ListingPriceSnapshotRepository snapshotRepo;
    @Mock MarketplaceService marketplaceService;

    @InjectMocks SearchPipelineService pipeline;

    private static final long PRODUCT = 42L;
    private static final Instant RUN_STARTED = Instant.parse("2026-10-30T09:00:00Z");

    private final List<CompetitorListing> saved = new ArrayList<>();
    private final List<ListingPriceSnapshot> snapshots = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(listingRepo.save(any())).thenAnswer(inv -> {
            saved.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(snapshotRepo.save(any())).thenAnswer(inv -> {
            snapshots.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        // No history by default, so any price counts as a move.
        when(snapshotRepo.findFirstByMarketplaceAndStorefrontAndMarketplaceItemIdOrderByObservedAtDesc(
                anyString(), anyString(), anyString())).thenReturn(Optional.empty());
    }

    private CompetitorListing listing(String itemId, Instant lastSeen, BigDecimal price) {
        CompetitorListing l = CompetitorListing.builder()
                .productId(PRODUCT)
                .marketplace("AMAZON")
                .storefront("US")
                .marketplaceItemId(itemId)
                .matchStatus(MatchStatus.MATCHED)
                .lastPrice(price)
                .currency("USD")
                .lastSeenAt(lastSeen)
                .build();
        l.setId(Math.abs((long) itemId.hashCode()));
        return l;
    }

    private void stale(CompetitorListing... listings) {
        when(listingRepo.findStaleForProduct(eq(PRODUCT), any(), any(Pageable.class)))
                .thenReturn(List.of(listings));
    }

    private PriceSnapshot price(String amount, String shipping) {
        return PriceSnapshot.builder()
                .itemPrice(new BigDecimal(amount))
                .shipping(shipping == null ? null : new BigDecimal(shipping))
                .currency("USD")
                .build();
    }

    @Nested
    @DisplayName("a listing that is still on sale")
    class StillListed {

        @Test
        @DisplayName("is re-priced and stamped as seen just now")
        void priceAndTimestampsAreUpdated() {
            CompetitorListing old = listing("B01", RUN_STARTED.minus(21, ChronoUnit.DAYS),
                    new BigDecimal("26.95"));
            stale(old);
            when(marketplaceService.getPrice(Marketplace.AMAZON, "B01", "US"))
                    .thenReturn(price("24.50", "3.99"));

            var outcome = pipeline.recheckAttachedListings(PRODUCT, RUN_STARTED, 50);

            assertThat(outcome.rechecked()).isEqualTo(1);
            assertThat(old.getLastPrice()).isEqualByComparingTo("24.50");
            assertThat(old.getShipping()).isEqualByComparingTo("3.99");
            assertThat(old.getAvailability()).isEqualTo("IN_STOCK");
            // Both move: we looked, and it was there.
            assertThat(old.getLastSeenAt()).isAfter(RUN_STARTED);
            assertThat(old.getLastFetchedAt()).isAfter(RUN_STARTED);
        }

        @Test
        @DisplayName("records a snapshot when the price moved")
        void movedPriceIsRecorded() {
            stale(listing("B01", RUN_STARTED.minus(9, ChronoUnit.DAYS), new BigDecimal("26.95")));
            when(marketplaceService.getPrice(any(), anyString(), anyString()))
                    .thenReturn(price("24.50", null));

            pipeline.recheckAttachedListings(PRODUCT, RUN_STARTED, 50);

            assertThat(snapshots).hasSize(1);
            assertThat(snapshots.get(0).getItemPrice()).isEqualByComparingTo("24.50");
            assertThat(snapshots.get(0).getMarketplaceItemId()).isEqualTo("B01");
            assertThat(snapshots.get(0).getStorefront()).isEqualTo("US");
        }

        @Test
        @DisplayName("records nothing when the price held, but still says it was checked")
        void steadyPriceWritesNoHistory() {
            stale(listing("B01", RUN_STARTED.minus(9, ChronoUnit.DAYS), new BigDecimal("26.95")));
            when(marketplaceService.getPrice(any(), anyString(), anyString()))
                    .thenReturn(price("26.95", null));
            when(snapshotRepo.findFirstByMarketplaceAndStorefrontAndMarketplaceItemIdOrderByObservedAtDesc(
                    "AMAZON", "US", "B01"))
                    .thenReturn(Optional.of(ListingPriceSnapshot.builder()
                            .itemPrice(new BigDecimal("26.95")).build()));

            var outcome = pipeline.recheckAttachedListings(PRODUCT, RUN_STARTED, 50);

            // FR-PRICE-003. A duplicate row would make a steady price
            // indistinguishable from an unchecked one — the very confusion this
            // whole pass exists to end.
            assertThat(snapshots).isEmpty();
            assertThat(outcome.rechecked()).isEqualTo(1);
            verify(listingRepo).save(any());
        }
    }

    @Nested
    @DisplayName("a listing that has ended")
    class Gone {

        @Test
        @DisplayName("is marked out of stock and is not an error")
        void endedListingIsAnAnswer() {
            CompetitorListing old = listing("B01", RUN_STARTED.minus(30, ChronoUnit.DAYS),
                    new BigDecimal("26.95"));
            stale(old);
            when(marketplaceService.getPrice(any(), anyString(), anyString()))
                    .thenThrow(new ResourceNotFoundException("Listing not found: B01"));

            var outcome = pipeline.recheckAttachedListings(PRODUCT, RUN_STARTED, 50);

            assertThat(outcome.gone()).isEqualTo(1);
            // A competitor leaving the market is a fact about the market, not a
            // fault in the run.
            assertThat(outcome.failed()).isZero();
            assertThat(old.getAvailability()).isEqualTo("OUT_OF_STOCK");
        }

        @Test
        @DisplayName("keeps its last known price and its old lastSeenAt")
        void lastSeenStaysWhereItWas() {
            Instant seen = RUN_STARTED.minus(30, ChronoUnit.DAYS);
            CompetitorListing old = listing("B01", seen, new BigDecimal("26.95"));
            stale(old);
            when(marketplaceService.getPrice(any(), anyString(), anyString())).thenReturn(null);

            pipeline.recheckAttachedListings(PRODUCT, RUN_STARTED, 50);

            // lastFetchedAt moves (we looked), lastSeenAt does not (it was not
            // there) — which is what lets the screen say how long it has been
            // gone instead of printing today's date on an offer nobody can buy.
            assertThat(old.getLastSeenAt()).isEqualTo(seen);
            assertThat(old.getLastFetchedAt()).isAfter(RUN_STARTED);
            assertThat(old.getLastPrice()).isEqualByComparingTo("26.95");
            assertThat(snapshots).isEmpty();
        }
    }

    @Nested
    @DisplayName("when something goes wrong")
    class Failures {

        @Test
        @DisplayName("one unreachable listing does not stop the others")
        void oneFailureDoesNotEndThePass() {
            stale(listing("B01", RUN_STARTED.minus(5, ChronoUnit.DAYS), null),
                    listing("B02", RUN_STARTED.minus(4, ChronoUnit.DAYS), null));
            when(marketplaceService.getPrice(any(), eq("B01"), anyString()))
                    .thenThrow(new MarketplaceApiException("Amazon timed out"));
            when(marketplaceService.getPrice(any(), eq("B02"), anyString()))
                    .thenReturn(price("19.99", null));

            var outcome = pipeline.recheckAttachedListings(PRODUCT, RUN_STARTED, 50);

            assertThat(outcome.failed()).isEqualTo(1);
            assertThat(outcome.rechecked()).isEqualTo(1);
        }

        @Test
        @DisplayName("nothing stale means no marketplace calls at all")
        void nothingStaleCostsNothing() {
            when(listingRepo.findStaleForProduct(eq(PRODUCT), any(), any(Pageable.class)))
                    .thenReturn(List.of());

            var outcome = pipeline.recheckAttachedListings(PRODUCT, RUN_STARTED, 50);

            assertThat(outcome.summary()).isNull();
            verify(marketplaceService, never()).getPrice(any(), anyString(), anyString());
        }

        @Test
        @DisplayName("a limit of zero does not even query")
        void zeroLimitDoesNothing() {
            pipeline.recheckAttachedListings(PRODUCT, RUN_STARTED, 0);

            verify(listingRepo, never()).findStaleForProduct(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("what the monitor is told")
    class Reporting {

        @Test
        @DisplayName("the cap is passed to the query, so a big product cannot run away")
        void limitReachesTheQuery() {
            when(listingRepo.findStaleForProduct(eq(PRODUCT), any(), any(Pageable.class)))
                    .thenReturn(List.of());

            pipeline.recheckAttachedListings(PRODUCT, RUN_STARTED, 50);

            ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
            verify(listingRepo).findStaleForProduct(eq(PRODUCT), eq(RUN_STARTED), page.capture());
            assertThat(page.getValue().getPageSize()).isEqualTo(50);
        }

        @Test
        @DisplayName("the summary names each outcome separately")
        void summaryReadsPlainly() {
            assertThat(new SearchPipelineService.RecheckOutcome(37, 2, 0).summary())
                    .isEqualTo("37 re-checked · 2 no longer listed");
            assertThat(new SearchPipelineService.RecheckOutcome(5, 0, 1).summary())
                    .isEqualTo("5 re-checked · 1 could not be read");
            // "We looked and it is gone" and "we could not look" must never
            // share a number.
            assertThat(new SearchPipelineService.RecheckOutcome(0, 0, 0).summary()).isNull();
        }
    }
}
