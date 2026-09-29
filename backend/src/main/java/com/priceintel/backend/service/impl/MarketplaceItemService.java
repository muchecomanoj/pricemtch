package com.priceintel.backend.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceintel.backend.dto.response.MatchedListing;
import com.priceintel.backend.entity.ListingPriceSnapshot;
import com.priceintel.backend.entity.MarketplaceItem;
import com.priceintel.backend.entity.PriceChangeEvent;
import com.priceintel.backend.repository.ListingPriceSnapshotRepository;
import com.priceintel.backend.repository.MarketplaceItemRepository;
import com.priceintel.backend.repository.PriceChangeEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns a search result into tracked history (FR-PRICE-003).
 *
 * <p>Every observation updates {@code lastSeenAt}. Only a <em>changed</em>
 * observation writes a snapshot and a change event. That distinction is the
 * whole point: 41% of the snapshots already in this database repeat the previous
 * value, which inflates storage, makes any volatility measure read calmer than
 * the market really is, and leaves "unchanged" indistinguishable from
 * "unchecked" — the one thing last-seen exists to tell apart.</p>
 *
 * <p>Rows are keyed by marketplace and item, never by tenant. A price is a fact
 * about the market, so two clients watching the same ASIN build one history
 * between them rather than two thin ones.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketplaceItemService {

    private final MarketplaceItemRepository itemRepo;
    private final PriceChangeEventRepository changeRepo;
    private final ListingPriceSnapshotRepository snapshotRepo;
    private final ObjectMapper objectMapper;
    private final com.priceintel.backend.service.MarketplaceService marketplaceService;
    private final com.priceintel.backend.repository.TenantTrackedItemRepository trackedRepo;

    /** What one pass of recording did, for the caller's log line. */
    public record Recorded(int seen, int created, int changed) {
    }

    /**
     * Records the listings a search judged to be this product.
     *
     * <p>Runs in its own transaction and never propagates a failure: history is
     * a side effect of searching, and a search that found the right answer must
     * not be reported as failed because a write to the archive went wrong.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Recorded record(List<MatchedListing> listings) {
        if (listings == null || listings.isEmpty()) {
            return new Recorded(0, 0, 0);
        }
        Instant now = Instant.now();
        // Whose search this was. The listing's history is shared; this link is
        // what puts its price changes in this company's feed and no one else's.
        Long tenantId = com.priceintel.backend.security.TenantContext.getTenantId();
        int created = 0;
        int changed = 0;
        for (MatchedListing l : listings) {
            if (l.getMarketplace() == null || l.getMarketplaceItemId() == null
                    || l.getMarketplaceItemId().isBlank()) {
                continue;
            }
            try {
                String storefront = storefrontOf(l);
                boolean isNew = itemRepo.findByMarketplaceAndStorefrontAndMarketplaceItemId(
                        l.getMarketplace(), storefront, l.getMarketplaceItemId()).isEmpty();
                if (recordOne(l, storefront, now)) {
                    changed++;
                }
                // Guarded rather than caught: a failed statement aborts the whole
                // Postgres transaction, and every listing after it would be lost.
                if (tenantId != null && storefront != null) {
                    trackedRepo.touch(tenantId, l.getMarketplace(), storefront, l.getMarketplaceItemId());
                }
                if (isNew) {
                    created++;
                }
            } catch (RuntimeException e) {
                // One bad listing must not cost the rest their history.
                log.warn("Could not record {} {}: {}", l.getMarketplace(),
                        l.getMarketplaceItemId(), e.getMessage());
            }
        }
        return new Recorded(listings.size(), created, changed);
    }

    /**
     * The storefront this listing was observed in.
     *
     * <p>What the marketplace call reported, else the page it lives on, else the
     * currency it was priced in. Resolved once per listing so the item row, its
     * snapshot and its change events can never disagree.</p>
     */
    private String storefrontOf(MatchedListing l) {
        return com.priceintel.backend.utils.Storefront.resolve(
                l.getStorefront(), l.getUrl(), l.getCurrency());
    }

    /** @return true when something actually moved */
    private boolean recordOne(MatchedListing l, String storefront, Instant now) {
        MarketplaceItem item = itemRepo
                .findByMarketplaceAndStorefrontAndMarketplaceItemId(
                        l.getMarketplace(), storefront, l.getMarketplaceItemId())
                .orElse(null);

        BigDecimal price = l.getPrice();
        BigDecimal shipping = l.getShipping();
        BigDecimal landed = landed(price, shipping);

        if (item == null) {
            item = MarketplaceItem.builder()
                    .marketplace(l.getMarketplace())
                    .storefront(storefront)
                    .marketplaceItemId(l.getMarketplaceItemId())
                    .firstSeenAt(now)
                    .observationCount(0).changeCount(0)
                    .build();
            applyCurrent(item, l, price, shipping, landed);
            item.setLastSeenAt(now);
            item.setObservationCount(1);
            // A first sighting is not a change — there is nothing it moved from.
            // Recording one would put a phantom event at the head of every
            // item's history, and every "what changed" list would open with it.
            item.setLastChangedAt(price != null ? now : null);
            itemRepo.save(item);
            if (price != null) {
                writeSnapshot(l, storefront, price, shipping, landed, now);
            }
            return false;
        }

        List<PriceChangeEvent> events = diff(item, l, price, shipping, landed, now);

        item.setLastSeenAt(now);
        item.setObservationCount(item.getObservationCount() + 1);
        if (!events.isEmpty()) {
            item.setLastChangedAt(now);
            item.setChangeCount(item.getChangeCount() + events.size());
        }
        applyCurrent(item, l, price, shipping, landed);
        itemRepo.save(item);

        if (events.isEmpty()) {
            // Seen, unchanged. lastSeenAt above already records that we looked,
            // which is what FR-PRICE-003 asks for in place of a duplicate row.
            return false;
        }

        // Something moved — the only moment it is worth spending a second API
        // call to ask WHO. On a steady product this never runs.
        String previousSeller = item.getSellerId();
        String currentSeller = buyBoxSellerOf(item);
        if (currentSeller != null) {
            item.setSellerId(currentSeller);
        }
        Boolean sellerChanged = (previousSeller == null || currentSeller == null)
                ? null : !previousSeller.equals(currentSeller);
        events.forEach(e -> {
            e.setSellerId(currentSeller);
            e.setPreviousSellerId(previousSeller);
            e.setSellerChanged(sellerChanged);
        });
        if (Boolean.TRUE.equals(sellerChanged)) {
            log.info("{} {}: Buy Box moved {} -> {} alongside the price change",
                    item.getMarketplace(), item.getMarketplaceItemId(),
                    previousSeller, currentSeller);
        }

        changeRepo.saveAll(events);
        writeSnapshot(l, storefront, price, shipping, landed, now);
        return true;
    }

    /**
     * The merchant currently holding the Buy Box, or null when it cannot be
     * determined.
     *
     * <p>Best-effort by design. This runs inside recording a price change, and
     * a failure to identify the seller must not lose the price change itself —
     * a known move with an unknown seller is far more useful than no record at
     * all.</p>
     */
    private String buyBoxSellerOf(MarketplaceItem item) {
        try {
            var marketplace = com.priceintel.backend.marketplace.Marketplace
                    .valueOf(item.getMarketplace());
            // This storefront's offers only. The Buy Box on amazon.com says
            // nothing about who holds it on amazon.ca.
            var offers = marketplaceService.getOffers(
                    marketplace, item.getMarketplaceItemId(), null, item.getStorefront());
            if (offers == null || offers.isEmpty()) {
                return null;
            }
            return offers.stream()
                    .filter(o -> o.isBuyBoxWinner() && o.getSellerId() != null)
                    .map(o -> o.getSellerId())
                    .findFirst()
                    // No flagged winner — an item can have offers and no Buy
                    // Box. Naming the cheapest as the holder would be a guess.
                    .orElse(null);
        } catch (RuntimeException e) {
            log.debug("Could not identify the Buy Box seller for {} {}: {}",
                    item.getMarketplace(), item.getMarketplaceItemId(), e.getMessage());
            return null;
        }
    }

    /** The fields that moved since the last observation. */
    private List<PriceChangeEvent> diff(MarketplaceItem item, MatchedListing l,
            BigDecimal price, BigDecimal shipping, BigDecimal landed, Instant now) {
        List<PriceChangeEvent> events = new ArrayList<>();
        Instant previous = item.getLastSeenAt();
        String currency = l.getCurrency() != null ? l.getCurrency() : item.getCurrency();

        addMoney(events, item, PriceChangeEvent.PRICE, item.getItemPrice(), price,
                currency, now, previous);
        addMoney(events, item, PriceChangeEvent.SHIPPING, item.getShipping(), shipping,
                currency, now, previous);
        addMoney(events, item, PriceChangeEvent.LANDED_PRICE, item.getLandedPrice(), landed,
                currency, now, previous);

        // Going out of stock is a competitive event in its own right, and one a
        // price series cannot show: an unavailable listing often keeps its last
        // price, so the chart stays flat while the competitor has left the market.
        String was = item.getAvailability();
        String is = l.getAvailability();
        if (is != null && !Objects.equals(was, is)) {
            events.add(base(item, PriceChangeEvent.AVAILABILITY, now, previous)
                    .oldValue(was).newValue(is).build());
        }
        return events;
    }

    private void addMoney(List<PriceChangeEvent> events, MarketplaceItem item, String field,
            BigDecimal was, BigDecimal is, String currency, Instant now, Instant previous) {
        // A value that disappears is not a change to zero. Amazon returns no
        // price when nobody holds the Buy Box, and writing "fell to 0" would
        // read as a catastrophic discount on a product that is merely unsold.
        if (was == null || is == null || was.compareTo(is) == 0) {
            return;
        }
        BigDecimal delta = is.subtract(was);
        BigDecimal pct = was.signum() == 0 ? null
                : delta.multiply(BigDecimal.valueOf(100)).divide(was, 4, RoundingMode.HALF_UP);
        events.add(base(item, field, now, previous)
                .oldValue(was.toPlainString()).newValue(is.toPlainString())
                .changeAmount(delta).changePct(pct).currency(currency)
                .build());
    }

    private PriceChangeEvent.PriceChangeEventBuilder base(MarketplaceItem item, String field,
            Instant now, Instant previous) {
        return PriceChangeEvent.builder()
                .marketplace(item.getMarketplace())
                .storefront(item.getStorefront())
                .marketplaceItemId(item.getMarketplaceItemId())
                .field(field).observedAt(now).previousObservedAt(previous);
    }

    private void applyCurrent(MarketplaceItem item, MatchedListing l,
            BigDecimal price, BigDecimal shipping, BigDecimal landed) {
        item.setTitle(l.getTitle());
        item.setUrl(com.priceintel.backend.utils.ListingUrl.fit(l.getUrl()));
        item.setSeller(l.getSeller());
        item.setCondition(l.getCondition());
        if (l.getCurrency() != null) {
            item.setCurrency(l.getCurrency());
        }
        if (l.getAvailability() != null) {
            item.setAvailability(l.getAvailability());
        }
        // Only overwrite a known price with another known price. A fetch that
        // came back without one means "not offered right now", not "free".
        if (price != null) {
            item.setItemPrice(price);
            item.setShipping(shipping);
            item.setLandedPrice(landed);
        }
        if (l.getIdentifiers() != null && !l.getIdentifiers().isEmpty()) {
            try {
                item.setIdentifiers(objectMapper.writeValueAsString(l.getIdentifiers()));
            } catch (Exception e) {
                log.debug("Could not serialise identifiers: {}", e.getMessage());
            }
        }
    }

    private void writeSnapshot(MatchedListing l, String storefront, BigDecimal price,
            BigDecimal shipping, BigDecimal landed, Instant now) {
        snapshotRepo.save(ListingPriceSnapshot.builder()
                // No listing row behind this one: it was seen on a marketplace
                // search, not as a competitor of anybody's product. The series
                // is read by marketplace item, so history still joins up if the
                // same item is later attached to a product.
                .marketplace(l.getMarketplace())
                .storefront(storefront)
                .marketplaceItemId(l.getMarketplaceItemId())
                .itemPrice(price).shipping(shipping).landedPrice(landed)
                .currency(l.getCurrency())
                .observedAt(now)
                .build());
    }

    private BigDecimal landed(BigDecimal price, BigDecimal shipping) {
        if (price == null) {
            return null;
        }
        return shipping == null ? price : price.add(shipping);
    }
}
