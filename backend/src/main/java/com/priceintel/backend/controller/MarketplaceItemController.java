package com.priceintel.backend.controller;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.entity.ListingPriceSnapshot;
import com.priceintel.backend.entity.MarketplaceItem;
import com.priceintel.backend.entity.PriceChangeEvent;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.exception.ResourceNotFoundException;
import com.priceintel.backend.repository.ListingPriceSnapshotRepository;
import com.priceintel.backend.repository.MarketplaceItemRepository;
import com.priceintel.backend.repository.PriceChangeEventRepository;
import com.priceintel.backend.utils.Storefront;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Tracked marketplace listings and their price history (FR-PRICE-003).
 *
 * <p>Not tenant-scoped. These are observations of a public marketplace, shared
 * so that every client watching an ASIN reads one history rather than a private
 * fragment of it. Nothing here reveals which client searched for what — only
 * that the item was seen and what it cost.</p>
 *
 * <p>A tracked item is one listing in one storefront. The same ASIN on amazon.com
 * and on amazon.ca is two tracked items with two separate histories.</p>
 */
@RestController
@RequestMapping("/api/v1/marketplace-items")
@RequiredArgsConstructor
@Tag(name = "Marketplace Item History",
        description = "Listings seen on marketplace searches, their prices over time, and every change")
public class MarketplaceItemController {

    private static final String STOREFRONT_PARAM_DOC =
            "\n\n`storefront` (`US`, `CA`, `GB`) picks one storefront's copy of the item. When "
                    + "omitted, the most recently seen copy is used; its `storefront` field says "
                    + "which one that was.";

    private final MarketplaceItemRepository itemRepo;
    private final PriceChangeEventRepository changeRepo;
    private final ListingPriceSnapshotRepository snapshotRepo;

    @GetMapping
    @Operation(summary = "Tracked listings, newest sighting first",
            description = "Everything a search has judged to be a real product. `changeCount` "
                    + "and `lastChangedAt` say whether a price has actually moved, which "
                    + "`lastSeenAt` alone cannot. Each row is one storefront's copy of an item.")
    public ResponseEntity<ApiResponse<Page<MarketplaceItem>>> list(
            @RequestParam(required = false) String marketplace,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "lastSeenAt"));
        return ResponseEntity.ok(ApiResponse.success(
                itemRepo.searchItems(companyScope(), marketplace, search, pageable),
                "Tracked marketplace items"));
    }

    @GetMapping("/{marketplace}/{itemId}")
    @Operation(summary = "One tracked listing", description = "One storefront's copy." + STOREFRONT_PARAM_DOC)
    public ResponseEntity<ApiResponse<MarketplaceItem>> item(
            @PathVariable String marketplace, @PathVariable String itemId,
            @RequestParam(required = false) String storefront) {
        return ResponseEntity.ok(ApiResponse.success(
                find(marketplace, itemId, storefront), "Tracked item"));
    }

    @GetMapping("/{marketplace}/{itemId}/price-history")
    @Operation(summary = "Every price recorded for this listing, oldest first",
            description = "The series that draws the chart. Only changed observations are "
                    + "stored, so consecutive points are always genuinely different prices — "
                    + "a flat stretch means the price held, not that nobody looked. Always one "
                    + "storefront, so one currency." + STOREFRONT_PARAM_DOC)
    public ResponseEntity<ApiResponse<List<ListingPriceSnapshot>>> history(
            @PathVariable String marketplace, @PathVariable String itemId,
            @RequestParam(required = false) String storefront,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        MarketplaceItem item = find(marketplace, itemId, storefront);
        String mp = item.getMarketplace();
        String sf = item.getStorefront();
        // The date-bounded read used to match the item id alone, across every
        // marketplace — so a filtered chart could include another marketplace's
        // listing that happened to share the id. Both reads are now keyed fully.
        List<ListingPriceSnapshot> series = (from == null && to == null)
                ? snapshotRepo.findByMarketplaceAndStorefrontAndMarketplaceItemIdOrderByObservedAtAsc(
                        mp, sf, itemId)
                : snapshotRepo
                        .findByMarketplaceAndStorefrontAndMarketplaceItemIdAndObservedAtBetweenOrderByObservedAtAsc(
                                mp, sf, itemId,
                                from != null ? from : Instant.EPOCH,
                                to != null ? to : Instant.now());
        return ResponseEntity.ok(ApiResponse.success(series, "Price history"));
    }

    @GetMapping("/{marketplace}/{itemId}/changes")
    @Operation(summary = "What changed on this listing, and when",
            description = "One entry per field that moved — price, shipping, landed price or "
                    + "availability — with the old and new value, the size of the move, and "
                    + "when the previous value was seen." + STOREFRONT_PARAM_DOC)
    public ResponseEntity<ApiResponse<Page<PriceChangeEvent>>> changes(
            @PathVariable String marketplace, @PathVariable String itemId,
            @RequestParam(required = false) String storefront,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        MarketplaceItem item = find(marketplace, itemId, storefront);
        Page<PriceChangeEvent> events =
                changeRepo.findByMarketplaceAndStorefrontAndMarketplaceItemIdOrderByObservedAtDesc(
                        item.getMarketplace(), item.getStorefront(), itemId,
                        PageRequest.of(page, size));
        // Every row here is this one listing, so its title is already known.
        events.getContent().forEach(e -> e.setTitle(item.getTitle()));
        return ResponseEntity.ok(ApiResponse.success(events, "Change history"));
    }

    @GetMapping("/changes")
    @Operation(summary = "Recent price changes across every tracked listing",
            description = "The feed for a Price Changes page. Filter by marketplace, by field "
                    + "(PRICE, SHIPPING, LANDED_PRICE, AVAILABILITY), or from a date.\n\n"
                    + "`search` matches the tracked listing's **title**, any **identifier** it "
                    + "carries (ASIN, UPC, EAN, GTIN, MPN) and the **item id** itself, so one box "
                    + "serves a product name, a barcode or an ASIN. Matched in the database "
                    + "before paging — filtering a page after it has been cut returns the wrong "
                    + "page and an untrustworthy total.\n\n"
                    + "Every event carries its `storefront` and the listing's `title`, so the feed "
                    + "reads without a lookup per row. `title` is null for changes recorded before "
                    + "the item was tracked.")
    public ResponseEntity<ApiResponse<Page<PriceChangeEvent>>> feed(
            @RequestParam(required = false) String marketplace,
            @RequestParam(required = false) String field,
            @RequestParam(required = false) String search,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<PriceChangeEvent> events =
                changeRepo.recent(companyScope(), marketplace, field, from, search, PageRequest.of(page, size));
        withTitles(events.getContent());
        return ResponseEntity.ok(ApiResponse.success(events, "Recent price changes"));
    }

    /**
     * Fills in each change's listing title, in one query for the page.
     *
     * <p>Keyed on marketplace, storefront and item id together: the same id in
     * two storefronts is two listings, and titles can differ between them.</p>
     */
    private void withTitles(List<PriceChangeEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        java.util.Set<String> ids = events.stream()
                .map(PriceChangeEvent::getMarketplaceItemId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (ids.isEmpty()) {
            return;
        }
        java.util.Map<String, String> titles = new java.util.HashMap<>();
        for (MarketplaceItem item : itemRepo.findByMarketplaceItemIdIn(ids)) {
            titles.put(key(item.getMarketplace(), item.getStorefront(), item.getMarketplaceItemId()),
                    item.getTitle());
        }
        events.forEach(e -> e.setTitle(
                titles.get(key(e.getMarketplace(), e.getStorefront(), e.getMarketplaceItemId()))));
    }

    private String key(String marketplace, String storefront, String itemId) {
        return marketplace + "|" + storefront + "|" + itemId;
    }

    private MarketplaceItem find(String marketplace, String itemId, String storefront) {
        String mp = marketplace.toUpperCase();
        if (storefront == null || storefront.isBlank()) {
            return itemRepo.findFirstByMarketplaceAndMarketplaceItemIdOrderByLastSeenAtDesc(mp, itemId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Not tracked: " + marketplace + " " + itemId));
        }
        String sf = Storefront.normalise(storefront);
        if (sf == null) {
            throw new BadRequestException("Unknown storefront: " + storefront
                    + ". Use a two-letter country code such as US, CA or GB.");
        }
        return itemRepo.findByMarketplaceAndStorefrontAndMarketplaceItemId(mp, sf, itemId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Not tracked: " + marketplace + " " + itemId + " in " + sf));
    }

    /**
     * Whose listings the lists show. The price history of a listing is shared,
     * but which listings a company is watching is its own business: a client
     * created minutes earlier was shown 55 changes on products other clients
     * had been researching. The platform owner sees everything; a caller with
     * no company sees nothing.
     */
    private static long companyScope() {
        if (com.priceintel.backend.security.TenantContext.isSuperAdmin()) {
            return com.priceintel.backend.repository.PriceChangeEventRepository.ALL_COMPANIES;
        }
        Long tenantId = com.priceintel.backend.security.TenantContext.getTenantId();
        return tenantId != null ? tenantId : Long.MIN_VALUE;   // matches no company
    }
}
