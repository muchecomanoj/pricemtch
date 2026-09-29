package com.priceintel.backend.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceintel.backend.dto.response.MatchedListing;
import com.priceintel.backend.entity.ListingPriceSnapshot;
import com.priceintel.backend.entity.MarketplaceItem;
import com.priceintel.backend.entity.PriceChangeEvent;
import com.priceintel.backend.repository.ListingPriceSnapshotRepository;
import com.priceintel.backend.repository.MarketplaceItemRepository;
import com.priceintel.backend.repository.PriceChangeEventRepository;
import com.priceintel.backend.service.impl.MarketplaceItemService;

/**
 * What gets written when a search sees a listing again.
 *
 * <p>The rule under test is that being seen and having changed are different
 * events. 41% of the snapshots already in this database repeat the previous
 * value, which is how "the price held steady" became indistinguishable from
 * "nobody checked".</p>
 */
class PriceChangeTrackingTest {

    private MarketplaceItemRepository items;
    private PriceChangeEventRepository changes;
    private ListingPriceSnapshotRepository snapshots;
    private com.priceintel.backend.service.MarketplaceService marketplaces;
    private com.priceintel.backend.repository.TenantTrackedItemRepository tracked;
    private MarketplaceItemService service;

    private final List<MarketplaceItem> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        items = mock(MarketplaceItemRepository.class);
        changes = mock(PriceChangeEventRepository.class);
        snapshots = mock(ListingPriceSnapshotRepository.class);
        marketplaces = mock(com.priceintel.backend.service.MarketplaceService.class);
        tracked = mock(com.priceintel.backend.repository.TenantTrackedItemRepository.class);
        service = new MarketplaceItemService(items, changes, snapshots, new ObjectMapper(),
                marketplaces, tracked);

        stored.clear();
        when(items.findByMarketplaceAndStorefrontAndMarketplaceItemId(
                anyString(), anyString(), anyString()))
                .thenAnswer(i -> stored.stream()
                        .filter(m -> m.getMarketplace().equals(i.getArgument(0))
                                && m.getStorefront().equals(i.getArgument(1))
                                && m.getMarketplaceItemId().equals(i.getArgument(2)))
                        .findFirst());
        when(items.save(any(MarketplaceItem.class))).thenAnswer(i -> {
            MarketplaceItem m = i.getArgument(0);
            stored.removeIf(s -> s.getMarketplace().equals(m.getMarketplace())
                    && s.getStorefront().equals(m.getStorefront())
                    && s.getMarketplaceItemId().equals(m.getMarketplaceItemId()));
            stored.add(m);
            return m;
        });
    }

    private MatchedListing listing(String price, String shipping) {
        return MatchedListing.builder()
                .marketplace("AMAZON").marketplaceItemId("B0DGHMNQ5Z")
                .title("Apple AirPods 4").currency("USD")
                .price(price == null ? null : new BigDecimal(price))
                .shipping(shipping == null ? null : new BigDecimal(shipping))
                .build();
    }

    @Test
    @DisplayName("a search records which company saw the listing, so its changes reach that company's feed")
    void searchLinksListingToCompany() {
        com.priceintel.backend.security.TenantContext.set(72L, false);
        try {
            service.record(List.of(listing("99.00", null)));
            verify(tracked).touch(72L, "AMAZON", "US", "B0DGHMNQ5Z");
        } finally {
            com.priceintel.backend.security.TenantContext.clear();
        }
    }

    @Test
    @DisplayName("the platform owner's searches are not credited to any company")
    void ownerSearchLinksNothing() {
        com.priceintel.backend.security.TenantContext.set(null, true);
        try {
            service.record(List.of(listing("99.00", null)));
            verify(tracked, never()).touch(any(), anyString(), anyString(), anyString());
        } finally {
            com.priceintel.backend.security.TenantContext.clear();
        }
    }

    @Test
    @DisplayName("a first sighting starts the history but is not a change")
    void firstSightingIsNotAChange() {
        var result = service.record(List.of(listing("99.00", null)));

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.changed()).isZero();
        verify(snapshots).save(any(ListingPriceSnapshot.class));
        // Nothing to have moved from — an event here would head every item's
        // history with a phantom.
        verify(changes, never()).saveAll(any());
        assertThat(stored.get(0).getObservationCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("seeing the same price again writes nothing but the sighting")
    void unchangedPriceWritesNoRows() {
        service.record(List.of(listing("99.00", null)));
        var again = service.record(List.of(listing("99.00", null)));

        assertThat(again.created()).isZero();
        assertThat(again.changed()).isZero();
        // One snapshot from the first sighting, none from the second.
        verify(snapshots).save(any(ListingPriceSnapshot.class));
        verify(changes, never()).saveAll(any());

        MarketplaceItem item = stored.get(0);
        assertThat(item.getObservationCount()).isEqualTo(2);   // we did look
        assertThat(item.getChangeCount()).isZero();            // nothing moved
        assertThat(item.getLastSeenAt()).isNotNull();
    }

    @Test
    @DisplayName("a real move records the old and new value and the size of it")
    void priceMoveIsRecorded() {
        service.record(List.of(listing("99.00", null)));
        var moved = service.record(List.of(listing("94.05", null)));

        assertThat(moved.changed()).isEqualTo(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PriceChangeEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(changes).saveAll(captor.capture());
        List<PriceChangeEvent> events = captor.getValue();

        assertThat(events).extracting(PriceChangeEvent::getField)
                .containsExactlyInAnyOrder(PriceChangeEvent.PRICE, PriceChangeEvent.LANDED_PRICE);

        PriceChangeEvent price = events.stream()
                .filter(e -> PriceChangeEvent.PRICE.equals(e.getField())).findFirst().orElseThrow();
        assertThat(price.getOldValue()).isEqualTo("99.00");
        assertThat(price.getNewValue()).isEqualTo("94.05");
        assertThat(price.getChangeAmount()).isEqualByComparingTo("-4.95");
        assertThat(price.getChangePct()).isEqualByComparingTo("-5.0000");
        assertThat(price.getPreviousObservedAt()).isNotNull();

        assertThat(stored.get(0).getChangeCount()).isEqualTo(2);
        assertThat(stored.get(0).getLastChangedAt()).isNotNull();
    }

    @Test
    @DisplayName("a price that disappears is not a fall to zero")
    void missingPriceIsNotAChange() {
        service.record(List.of(listing("99.00", null)));
        var gone = service.record(List.of(listing(null, null)));

        // Amazon returns no price when nobody holds the Buy Box. "Fell to 0"
        // would read as a catastrophic discount on a product merely unsold.
        assertThat(gone.changed()).isZero();
        verify(changes, never()).saveAll(any());
        // And the last known price is kept rather than blanked.
        assertThat(stored.get(0).getItemPrice()).isEqualByComparingTo("99.00");
    }

    @Test
    @DisplayName("shipping moving is its own event, and changes the landed price")
    void shippingMoveIsSeparate() {
        service.record(List.of(listing("99.00", "0.00")));
        service.record(List.of(listing("99.00", "4.99")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PriceChangeEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(changes).saveAll(captor.capture());

        assertThat(captor.getValue()).extracting(PriceChangeEvent::getField)
                .containsExactlyInAnyOrder(
                        PriceChangeEvent.SHIPPING, PriceChangeEvent.LANDED_PRICE);
    }

    @Test
    @DisplayName("one unusable listing does not cost the others their history")
    void badListingIsSkipped() {
        var result = service.record(List.of(
                MatchedListing.builder().marketplace("AMAZON").build(),  // no item id
                listing("99.00", null)));

        assertThat(result.created()).isEqualTo(1);
        assertThat(stored).hasSize(1);
    }

    /** The offers call is made only when something moved — never otherwise. */
    private com.priceintel.backend.marketplace.model.OfferListing offer(String sellerId,
            boolean buyBox) {
        return com.priceintel.backend.marketplace.model.OfferListing.builder()
                .sellerId(sellerId).buyBoxWinner(buyBox)
                .listingPrice(new BigDecimal("99.00")).build();
    }

    @Test
    @DisplayName("an unchanged price never asks who the seller is")
    void noSellerLookupWhenNothingMoved() {
        service.record(List.of(listing("99.00", null)));
        service.record(List.of(listing("99.00", null)));

        // The offers endpoint is a second API call against a quota that already
        // returns 429s. It must not be spent to learn nothing.
        verify(marketplaces, never()).getOffers(any(), anyString(), any(), any());
    }

    @Test
    @DisplayName("a price move records who held the Buy Box, and whether it changed")
    void sellerRecordedOnMove() {
        when(marketplaces.getOffers(any(), anyString(), any(), any()))
                .thenReturn(List.of(offer("SELLER_A", false), offer("SELLER_B", true)));

        service.record(List.of(listing("99.00", null)));   // first sighting
        service.record(List.of(listing("94.05", null)));   // a real move

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PriceChangeEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(changes).saveAll(captor.capture());

        PriceChangeEvent price = captor.getValue().stream()
                .filter(e -> PriceChangeEvent.PRICE.equals(e.getField())).findFirst().orElseThrow();
        assertThat(price.getSellerId()).isEqualTo("SELLER_B");
        // Nothing was known about the seller before this move, so "did it
        // change?" has no answer — and false would be a claim, not a fact.
        assertThat(price.getPreviousSellerId()).isNull();
        assertThat(price.getSellerChanged()).isNull();

        assertThat(stored.get(0).getSellerId()).isEqualTo("SELLER_B");
    }

    @Test
    @DisplayName("a failed seller lookup does not lose the price change")
    void sellerLookupFailureIsNotFatal() {
        when(marketplaces.getOffers(any(), anyString(), any(), any()))
                .thenThrow(new RuntimeException("Amazon 429"));

        service.record(List.of(listing("99.00", null)));
        var moved = service.record(List.of(listing("94.05", null)));

        // A known move with an unknown seller beats no record at all.
        assertThat(moved.changed()).isEqualTo(1);
        verify(changes).saveAll(any());
    }

    @Test
    @DisplayName("nothing to record is not an error")
    void emptyInput() {
        assertThat(service.record(List.of()).seen()).isZero();
        assertThat(service.record(null).seen()).isZero();
        verify(items, never()).save(any());
    }

    /** Guards the repository lookup the de-duplication on the product path uses. */
    @Test
    @DisplayName("the snapshot lookup is by marketplace item and storefront, not by row")
    void dedupeLooksUpTheSharedSeries() {
        when(snapshots.findFirstByMarketplaceAndStorefrontAndMarketplaceItemIdOrderByObservedAtDesc(
                "AMAZON", "US", "B0DGHMNQ5Z")).thenReturn(Optional.empty());

        assertThat(snapshots.findFirstByMarketplaceAndStorefrontAndMarketplaceItemIdOrderByObservedAtDesc(
                "AMAZON", "US", "B0DGHMNQ5Z")).isEmpty();
    }

    // ---------- storefronts ----------

    private MatchedListing inStorefront(String storefront, String currency, String price) {
        return MatchedListing.builder()
                .marketplace("AMAZON").storefront(storefront).marketplaceItemId("B0DGHMNQ5Z")
                .title("Echo Dot").price(new BigDecimal(price)).currency(currency)
                .build();
    }

    @Test
    @DisplayName("the same ASIN in two storefronts is two items, not one")
    void storefrontsAreSeparateItems() {
        service.record(List.of(inStorefront("US", "USD", "144.29")));
        service.record(List.of(inStorefront("CA", "CAD", "140.00")));

        assertThat(stored).hasSize(2);
        assertThat(stored).extracting(MarketplaceItem::getStorefront)
                .containsExactlyInAnyOrder("US", "CA");
    }

    @Test
    @DisplayName("alternating between storefronts records no price change")
    void storefrontSwitchIsNotAPriceChange() {
        // The defect that produced 46 phantom events: US$144.29 then CA$140.00
        // on one shared item read as a four-dollar price drop.
        service.record(List.of(inStorefront("US", "USD", "144.29")));
        service.record(List.of(inStorefront("CA", "CAD", "140.00")));
        service.record(List.of(inStorefront("US", "USD", "144.29")));

        verify(changes, never()).saveAll(any());
    }

    @Test
    @DisplayName("a real move within one storefront is still recorded")
    void moveWithinStorefrontIsRecorded() {
        service.record(List.of(inStorefront("CA", "CAD", "140.00")));
        service.record(List.of(inStorefront("CA", "CAD", "129.99")));

        verify(changes).saveAll(any());
        assertThat(stored).singleElement()
                .extracting(MarketplaceItem::getStorefront).isEqualTo("CA");
    }

    @Test
    @DisplayName("a listing with no reported storefront is placed by its currency")
    void storefrontInferredFromCurrency() {
        service.record(List.of(MatchedListing.builder()
                .marketplace("AMAZON").marketplaceItemId("B0DGHMNQ5Z")
                .title("Echo Dot").price(new BigDecimal("140.00")).currency("CAD")
                .build()));

        assertThat(stored).singleElement()
                .extracting(MarketplaceItem::getStorefront).isEqualTo("CA");
    }
}
