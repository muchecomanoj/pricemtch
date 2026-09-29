package com.priceintel.backend.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.priceintel.backend.constants.IdentifierType;
import com.priceintel.backend.constants.MatchStatus;
import com.priceintel.backend.constants.ValueStatus;
import com.priceintel.backend.dto.request.MarketplaceSearchRequest;
import com.priceintel.backend.dto.response.CompetitorListingResponse;
import com.priceintel.backend.dto.response.MarketPricesResponse;
import com.priceintel.backend.dto.response.MatchSignal;
import com.priceintel.backend.dto.response.ProfitabilityResponse;
import com.priceintel.backend.dto.response.SearchJobResponse;
import com.priceintel.backend.entity.CompetitorListing;
import com.priceintel.backend.entity.ListingPriceSnapshot;
import com.priceintel.backend.entity.Product;
import com.priceintel.backend.entity.SearchJob;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.exception.ResourceNotFoundException;
import com.priceintel.backend.marketplace.Marketplace;
import com.priceintel.backend.marketplace.model.SearchResult;
import com.priceintel.backend.marketplace.model.SearchResultItem;
import com.priceintel.backend.repository.CompetitorListingRepository;
import com.priceintel.backend.repository.ListingPriceSnapshotRepository;
import com.priceintel.backend.repository.ProductRepository;
import com.priceintel.backend.repository.SearchJobRepository;
import com.priceintel.backend.security.TenantContext;
import com.priceintel.backend.service.MarketplaceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates a product search across marketplaces (FR-SRCH), persists the
 * candidate competitor listings + price snapshots, and serves candidates,
 * market-price stats, and price history.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchPipelineService {

    private final ProductRepository productRepo;
    private final SearchJobRepository jobRepo;
    private final CompetitorListingRepository listingRepo;
    private final ListingPriceSnapshotRepository snapshotRepo;
    private final MarketplaceService marketplaceService;
    private final MatchScoringService scorer;
    private final CompetitorSearchCacheService searchCache;
    private final CostProfileService costProfileService;
    private final com.priceintel.backend.utils.ValueStatusResolver statusResolver;
    private final AiMatchAdvisorService aiAdvisor;
    private final FxRateService fxRates;

    private static final List<Marketplace> DEFAULT_MARKETS =
            List.of(Marketplace.AMAZON, Marketplace.EBAY);

    // ---------- tenant isolation ----------

    /** The tenant to scope queries to, or null for the platform owner. */
    private Long currentTenantScope() {
        return TenantContext.isSuperAdmin() ? null : TenantContext.getTenantId();
    }

    /**
     * Loads a product the caller is actually entitled to see.
     *
     * <p>Reports "not found" rather than "forbidden" for another tenant's
     * product: confirming that an id exists would leak which products a
     * competing client tracks.</p>
     */
    private Product tenantProductOrThrow(Long productId) {
        Product product = productRepo.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        Long tenantId = currentTenantScope();
        if (tenantId != null && !tenantId.equals(product.getTenantId())) {
            throw new ResourceNotFoundException("Product not found: " + productId);
        }
        return product;
    }

    /** Loads a competitor listing the caller owns, or reports it as not found. */
    private CompetitorListing tenantListingOrThrow(Long listingId) {
        CompetitorListing listing = listingRepo.findById(listingId)
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found: " + listingId));
        Long tenantId = currentTenantScope();
        if (tenantId != null && !tenantId.equals(listing.getTenantId())) {
            throw new ResourceNotFoundException("Listing not found: " + listingId);
        }
        return listing;
    }

    // ---------- search job ----------

    @Transactional
    public SearchJobResponse runSearch(Long productId, List<String> markets, Integer maxResults) {
        return runSearch(productId, markets, maxResults, false);
    }

    /**
     * Searches the marketplaces for a product's competitors.
     *
     * <p>Results are shared: what a marketplace returns for a given query is a
     * fact about the marketplace, not about the client who asked, so a previous
     * fetch of the same query is reused instead of paying for it again. Because
     * the query is the product's identifier where one exists, two clients
     * tracking the same product resolve to the same key and the second one
     * costs no API call at all.</p>
     *
     * @param forceRefresh bypass the cache and re-fetch from the marketplace
     */
    @Transactional
    public SearchJobResponse runSearch(Long productId, List<String> markets, Integer maxResults,
                                       boolean forceRefresh) {
        return runSearch(productId, markets, maxResults, forceRefresh, null);
    }

    /**
     * @param maxAge reuse a cached fetch only if it is younger than this. Null
     *               applies the configured cache TTL. Scheduled monitors pass
     *               their own interval, so a monitor never reports data older
     *               than the cadence it promises.
     */
    @Transactional
    public SearchJobResponse runSearch(Long productId, List<String> markets, Integer maxResults,
                                       boolean forceRefresh, java.time.Duration maxAge) {
        return runSearch(productId, markets, maxResults, forceRefresh, maxAge, null);
    }

    /**
     * @param destination where the buyer is, so delivery can be priced. Null
     *                    leaves shipping unknown, and every landed price built
     *                    without it is reported as incomplete rather than as a
     *                    total (FR §12.1, UAT-04).
     */
    @Transactional
    public SearchJobResponse runSearch(Long productId, List<String> markets, Integer maxResults,
                                       boolean forceRefresh, java.time.Duration maxAge,
                                       com.priceintel.backend.dto.request.Destination destination) {
        Product product = tenantProductOrThrow(productId);
        List<Marketplace> targets = resolveMarkets(markets);
        String query = queryFor(product);
        int limit = maxResults != null && maxResults > 0 ? maxResults : 5;
        String correlationId = UUID.randomUUID().toString();

        SearchJob job = jobRepo.save(SearchJob.builder()
                .productId(productId)
                .markets(String.join(",", targets.stream().map(Enum::name).toList()))
                .status("RUNNING").correlationId(correlationId).build());

        // The title, used when an identifier search comes back empty.
        String fallbackQuery = titleFor(product);

        int total = 0;
        int fromCache = 0;
        boolean usedTitleFallback = false;
        List<String> errors = new ArrayList<>();
        for (Marketplace mp : targets) {
            try {
                // 1. Reuse a recent fetch of this exact query when we have one.
                if (!forceRefresh) {
                    var cached = searchCache.lookupWithin(mp, query, maxAge);
                    if (cached.isPresent()) {
                        for (CompetitorListing source : cached.get().listings()) {
                            copyToProduct(product, mp, source);
                            total++;
                            fromCache++;
                        }
                        continue;
                    }
                }

                // 2. Otherwise go to the marketplace, then remember what it gave us.
                SearchResult result = marketplaceService.search(mp,
                        MarketplaceSearchRequest.builder().query(query).maxResults(limit)
                                .destination(destination).build());
                String usedQuery = query;

                // 3. An identifier that returns nothing is usually wrong rather
                // than proof the product has no competitors — a mistyped or
                // invented ASIN matches nothing at all, while the title would
                // have found the product immediately. Retry with the title
                // rather than reporting an empty market.
                if (result.getItems().isEmpty() && !query.equals(fallbackQuery)) {
                    log.info("Search {} on {}: identifier '{}' matched nothing — retrying by title",
                            productId, mp, query);
                    SearchResult byTitle = marketplaceService.search(mp,
                            MarketplaceSearchRequest.builder()
                                    .query(fallbackQuery).maxResults(limit)
                                    .destination(destination).build());
                    if (!byTitle.getItems().isEmpty()) {
                        result = byTitle;
                        usedQuery = fallbackQuery;
                        usedTitleFallback = true;
                    }
                }

                List<String> itemIds = new ArrayList<>();
                for (SearchResultItem item : result.getItems()) {
                    upsertListing(product, mp, item);
                    // Cached with its storefront. A bare item id names the US and
                    // Canadian copies alike, and a later hit could not tell which
                    // one this query actually returned.
                    itemIds.add(CompetitorSearchCacheService.cacheKey(
                            storefrontOf(item), item.getMarketplaceItemId()));
                    total++;
                }
                searchCache.store(mp, usedQuery, itemIds);
            } catch (Exception e) {
                log.warn("Search {} on {} failed: {}", productId, mp, e.getMessage());
                errors.add(mp + ": " + e.getMessage());
            }
        }

        job.setResultCount(total);
        job.setStatus("COMPLETED");
        job.setFinishedAt(Instant.now());
        List<String> notes = new ArrayList<>();
        if (fromCache > 0) {
            notes.add(fromCache + " of " + total + " listing(s) served from cache (no API call)");
        }
        if (!errors.isEmpty()) {
            notes.add("Some sources failed — " + String.join("; ", errors));
        }
        if (usedTitleFallback) {
            // Say so: title matches are looser than identifier matches, and a
            // reviewer should know which they are looking at before confirming.
            notes.add("The product identifier matched nothing, so these were found by title — "
                    + "check the ASIN is correct");
        }
        if (!notes.isEmpty()) {
            job.setNote(String.join(". ", notes));
        }
        jobRepo.save(job);
        log.info("Search job {} for product {} (query '{}'): {} listings across {}, {} from cache",
                job.getId(), productId, query, total, targets, fromCache);
        return toJobResponse(job);
    }

    /**
     * Attaches an already-fetched listing to another product without calling the
     * marketplace.
     *
     * <p>Only the market facts are copied. The match status stays CANDIDATE and
     * the score is recomputed against <i>this</i> product, so one client's
     * accept/reject decision never leaks into another's queue. The original
     * fetch time is preserved rather than stamped as now — "as of" must describe
     * when the price was really read, not when it was copied.</p>
     */
    private void copyToProduct(Product product, Marketplace mp, CompetitorListing source) {
        Long productId = product.getId();
        if (productId.equals(source.getProductId())) {
            // Already this product's own row — nothing to copy, but the run did
            // return it, and that is the whole basis of "found on the last run".
            // Leaving early without stamping is why a fully cached run used to
            // report results no screen could show.
            source.setLastSeenAt(Instant.now());
            listingRepo.save(source);
            return;
        }
        // The source's own storefront, copied as-is: a Canadian listing reused for
        // another product is still the Canadian listing.
        String storefront = source.getStorefront();
        CompetitorListing listing = listingRepo
                .findByProductIdAndMarketplaceAndStorefrontAndMarketplaceItemId(
                        productId, mp.name(), storefront, source.getMarketplaceItemId())
                .orElseGet(() -> CompetitorListing.builder()
                        .productId(productId).marketplace(mp.name())
                        .storefront(storefront)
                        .marketplaceItemId(source.getMarketplaceItemId())
                        .matchStatus(MatchStatus.CANDIDATE).build());
        // The market facts are shared; the row belongs to this product's owner.
        listing.setTenantId(product.getTenantId());
        listing.setTitle(source.getTitle());
        listing.setUrl(com.priceintel.backend.utils.ListingUrl.fit(source.getUrl()));
        listing.setSeller(source.getSeller());
        listing.setCondition(source.getCondition());
        listing.setCurrency(source.getCurrency());
        listing.setLastPrice(source.getLastPrice());
        listing.setShipping(source.getShipping());
        listing.setAvailability(source.getAvailability());
        listing.setRating(source.getRating());
        listing.setSourceTimestamp(Instant.now());
        listing.setLastFetchedAt(source.getLastFetchedAt());
        listing.setLastSeenAt(Instant.now());
        listing.setMatchScore(scorer.assess(product, listing).getScore());
        listingRepo.save(listing);
        // No snapshot written: the price observation already exists against this
        // marketplace item, and history is read by item, not by row.
    }

    @Transactional(readOnly = true)
    public SearchJobResponse getJob(Long jobId) {
        return toJobResponse(jobRepo.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Search job not found: " + jobId)));
    }

    /** Creates or updates a competitor listing, scores it, and records a price snapshot. */
    private void upsertListing(Product product, Marketplace mp, SearchResultItem item) {
        Long productId = product.getId();
        // Keyed by storefront. Without it, a later search that happened to land
        // on amazon.com overwrote a Canadian listing's price in place.
        String storefront = storefrontOf(item);
        CompetitorListing listing = listingRepo
                .findByProductIdAndMarketplaceAndStorefrontAndMarketplaceItemId(
                        productId, mp.name(), storefront, item.getMarketplaceItemId())
                .orElseGet(() -> CompetitorListing.builder()
                        .productId(productId).marketplace(mp.name())
                        .storefront(storefront)
                        .marketplaceItemId(item.getMarketplaceItemId())
                        .matchStatus(MatchStatus.CANDIDATE).build());
        listing.setTenantId(product.getTenantId());
        listing.setTitle(item.getTitle());
        listing.setUrl(com.priceintel.backend.utils.ListingUrl.fit(item.getUrl()));
        listing.setSeller(item.getSeller());
        listing.setCondition(item.getCondition());
        listing.setCurrency(item.getCurrency());
        listing.setLastPrice(item.getPrice());
        listing.setShipping(item.getShipping());
        listing.setAvailability(item.getAvailability());
        listing.setRating(item.getRating());
        Instant now = Instant.now();
        listing.setSourceTimestamp(now);
        // This price really did come off the marketplace just now, so it anchors
        // the cache TTL and every "as of" the UI shows.
        listing.setLastFetchedAt(now);
        listing.setLastSeenAt(now);
        // FR-MATCH-002: persist the confidence score so the review queue can rank by it.
        listing.setMatchScore(scorer.assess(product, listing).getScore());
        listing = listingRepo.save(listing);

        // FR-PRICE-003: a snapshot only when something moved. lastSeenAt above
        // already records that we looked, which is what the requirement asks for
        // in place of a duplicate row. Writing one regardless left 216 of 532
        // snapshots repeating the previous value — 41% — which does not merely
        // waste space: it makes a steady price indistinguishable from an
        // unchecked one, and would make any volatility measure read calmer than
        // the market actually is.
        if (item.getPrice() != null && priceMoved(mp, storefront, item, now)) {
            snapshotRepo.save(ListingPriceSnapshot.builder()
                    .listingId(listing.getId())
                    // Keyed by the real-world listing too, so every tenant tracking
                    // this item reads one shared price series.
                    .marketplace(mp.name())
                    .storefront(storefront)
                    .marketplaceItemId(item.getMarketplaceItemId())
                    .itemPrice(item.getPrice())
                    .shipping(item.getShipping())
                    // Landed price is what a buyer actually pays, so shipping
                    // belongs in it. Comparing bare item prices flatters a cheap
                    // listing with expensive delivery against a free-shipping one.
                    .landedPrice(landedPrice(item.getPrice(), item.getShipping()))
                    .currency(item.getCurrency())
                    .observedAt(now)
                    .build());
        }
    }

    /**
     * Whether this observation differs from the last one recorded for the same
     * marketplace item.
     *
     * <p>Compared against the shared series rather than this tenant's rows: the
     * price of an ASIN is one fact, so if another client's search already
     * recorded today's price, recording it again adds nothing.</p>
     */
    private boolean priceMoved(Marketplace mp, String storefront, SearchResultItem item,
                               Instant now) {
        // Compared within one storefront. Across two, every alternation between
        // US$144 and CA$140 looked like a price move and wrote a snapshot.
        var last = snapshotRepo
                .findFirstByMarketplaceAndStorefrontAndMarketplaceItemIdOrderByObservedAtDesc(
                        mp.name(), storefront, item.getMarketplaceItemId());
        if (last.isEmpty()) {
            return true;
        }
        ListingPriceSnapshot prev = last.get();
        return !equalMoney(prev.getItemPrice(), item.getPrice())
                || !equalMoney(prev.getShipping(), item.getShipping());
    }

    /** The storefront a search result came from, from the best evidence it carries. */
    private static String storefrontOf(SearchResultItem item) {
        return com.priceintel.backend.utils.Storefront.resolve(
                item.getStorefront(), item.getUrl(), item.getCurrency());
    }

    /** Null-safe equality by value, so 99.00 and 99.000 are the same price. */
    private boolean equalMoney(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        return a.compareTo(b) == 0;
    }

    /**
     * One-time backfill: score any listing captured before match-scoring existed
     * (matchScore == null) so the review queue can rank them. Runs at startup and
     * is a no-op once all rows are scored.
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    @Transactional
    public void backfillMatchScores() {
        List<CompetitorListing> unscored = listingRepo.findByMatchScoreIsNull();
        if (unscored.isEmpty()) {
            return;
        }
        java.util.Set<Long> ids = unscored.stream()
                .map(CompetitorListing::getProductId).collect(java.util.stream.Collectors.toSet());
        java.util.Map<Long, Product> products = new java.util.HashMap<>();
        productRepo.findAllById(ids).forEach(p -> products.put(p.getId(), p));
        int done = 0;
        for (CompetitorListing l : unscored) {
            Product p = products.get(l.getProductId());
            if (p != null) {
                l.setMatchScore(scorer.assess(p, l).getScore());
                listingRepo.save(l);
                done++;
            }
        }
        log.info("Backfilled match scores for {} competitor listings", done);
    }

    /**
     * One-time backfill for rows captured before the shared cache existed:
     * stamps each listing's fetch time from its existing source timestamp, and
     * tags each price snapshot with the marketplace item it belongs to so the
     * old history joins the shared series. No-op once complete.
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    @Transactional
    public void backfillFetchProvenance() {
        List<CompetitorListing> unstamped = listingRepo.findByLastFetchedAtIsNull();
        for (CompetitorListing l : unstamped) {
            // The old sourceTimestamp was written at fetch time, so it is the
            // best available record of when the price was actually read.
            l.setLastFetchedAt(l.getSourceTimestamp() != null
                    ? l.getSourceTimestamp()
                    : (l.getCreatedAt() != null ? l.getCreatedAt().toInstant(ZoneOffset.UTC) : null));
        }
        if (!unstamped.isEmpty()) {
            listingRepo.saveAll(unstamped);
            log.info("Backfilled fetch time for {} competitor listings", unstamped.size());
        }

        List<ListingPriceSnapshot> untagged = snapshotRepo.findByMarketplaceItemIdIsNull();
        if (untagged.isEmpty()) {
            return;
        }
        java.util.Map<Long, CompetitorListing> byId = new java.util.HashMap<>();
        listingRepo.findAllById(untagged.stream()
                        .map(ListingPriceSnapshot::getListingId).distinct().toList())
                .forEach(l -> byId.put(l.getId(), l));
        int tagged = 0;
        for (ListingPriceSnapshot s : untagged) {
            CompetitorListing l = byId.get(s.getListingId());
            if (l != null) {
                s.setMarketplace(l.getMarketplace());
                s.setMarketplaceItemId(l.getMarketplaceItemId());
                tagged++;
            }
        }
        snapshotRepo.saveAll(untagged);
        log.info("Backfilled marketplace item on {} price snapshots", tagged);
    }

    /**
     * One-time backfill of the owning tenant onto listings captured before the
     * column existed. Until a row has a tenant it is invisible to every client
     * (the filter excludes nulls), so this must run before the screens are used.
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    @Transactional
    public void backfillListingTenants() {
        List<CompetitorListing> orphans = listingRepo.findByTenantIdIsNull();
        if (orphans.isEmpty()) {
            return;
        }
        java.util.Map<Long, Product> products = new java.util.HashMap<>();
        productRepo.findAllById(orphans.stream()
                        .map(CompetitorListing::getProductId).distinct().toList())
                .forEach(p -> products.put(p.getId(), p));
        int done = 0;
        for (CompetitorListing l : orphans) {
            Product p = products.get(l.getProductId());
            if (p != null && p.getTenantId() != null) {
                l.setTenantId(p.getTenantId());
                done++;
            }
        }
        listingRepo.saveAll(orphans);
        log.info("Backfilled tenant on {} of {} competitor listings", done, orphans.size());
    }

    // ---------- candidates + review ----------

    /**
     * The competitor listings shown on a product's Competitors tab.
     *
     * <p>Competitor research is product-level, not client-level. If another
     * client already looked up this same product — matched by identifier, so
     * the same ASIN in either catalogue — those results are adopted here on
     * first view instead of showing an empty page and asking for a search that
     * has already been paid for.</p>
     *
     * <p>Only the market facts are adopted. Match status starts fresh and the
     * score is recomputed against this product, so one client's accept/reject
     * decisions never appear in another's queue. Age is not a barrier to
     * display — the listing carries its true fetch time, and a stale price
     * shown honestly beats an empty screen. Refreshing is the user's call.</p>
     */
    @Transactional
    public List<CompetitorListingResponse> getCandidates(Long productId) {
        Product product = tenantProductOrThrow(productId);
        List<CompetitorListing> own = listingRepo.findByProductIdOrderByCreatedAtDesc(productId);

        if (own.isEmpty() && product != null) {
            int adopted = adoptSharedResearch(product);
            if (adopted > 0) {
                own = listingRepo.findByProductIdOrderByCreatedAtDesc(productId);
            }
        }
        final Product p = product;
        List<CompetitorListingResponse> responses =
                own.stream().map(l -> toListingResponse(l, p)).toList();
        markPriceHistory(own, responses);
        attachAiVerdicts(own, responses);
        return responses;
    }

    /**
     * Attaches any AI verdicts already held for these listings.
     *
     * <p>Read-only — this never triggers a model call. Rendering a list must not
     * spend quota, or opening the page twice would cost twice; judging is an
     * explicit action.</p>
     */
    private void attachAiVerdicts(List<CompetitorListing> listings,
            List<CompetitorListingResponse> responses) {
        if (listings.isEmpty() || aiAdvisor == null) {
            return;
        }
        var verdicts = aiAdvisor.cachedFor(
                listings.stream().map(CompetitorListing::getId).toList());
        if (verdicts.isEmpty()) {
            return;
        }
        for (int i = 0; i < listings.size(); i++) {
            var verdict = verdicts.get(listings.get(i).getId());
            if (verdict != null) {
                responses.get(i).setAiVerdict(CompetitorListingResponse.AiVerdict.builder()
                        .decision(verdict.getDecision())
                        .score(verdict.getScore())
                        .reason(verdict.getReason())
                        .provider(verdict.getProvider() == null ? null : verdict.getProvider().name())
                        .model(verdict.getModel())
                        .judgedAt(verdict.getCreatedAt() == null ? null
                                : verdict.getCreatedAt().toInstant(java.time.ZoneOffset.UTC))
                        .build());
            }
        }
    }

    /**
     * Flags which of these listings have a chartable price history.
     *
     * <p>Two queries for the whole product rather than one per row. Resolved the
     * same way {@link #priceHistory} reads: by real-world item where we have one,
     * falling back to the row id for listings that predate the item keys — so the
     * flag and the chart can never disagree about whether data exists.</p>
     */
    private void markPriceHistory(List<CompetitorListing> listings,
            List<CompetitorListingResponse> responses) {
        if (listings.isEmpty()) {
            return;
        }
        List<String> itemIds = listings.stream()
                .map(CompetitorListing::getMarketplaceItemId)
                .filter(id -> id != null && !id.isBlank())
                .distinct().toList();
        Set<String> itemKeys = itemIds.isEmpty() ? Set.of()
                : snapshotRepo.findItemKeysWithHistory(itemIds).stream()
                        .map(row -> itemKey((String) row[0], (String) row[1], (String) row[2]))
                        .collect(java.util.stream.Collectors.toSet());

        List<Long> legacyIds = listings.stream()
                .filter(l -> l.getMarketplaceItemId() == null || l.getMarketplaceItemId().isBlank())
                .map(CompetitorListing::getId).toList();
        Set<Long> legacyWithHistory = legacyIds.isEmpty() ? Set.of()
                : Set.copyOf(snapshotRepo.findListingIdsWithHistory(legacyIds));

        for (int i = 0; i < listings.size(); i++) {
            CompetitorListing l = listings.get(i);
            String itemId = l.getMarketplaceItemId();
            boolean has = itemId != null && !itemId.isBlank()
                    ? itemKeys.contains(itemKey(l.getMarketplace(), l.getStorefront(), itemId))
                    : legacyWithHistory.contains(l.getId());
            responses.get(i).setHasPriceHistory(has);
        }
    }

    /**
     * The identity of a real-world listing. All three parts: an item id is unique
     * neither across marketplaces nor across one marketplace's storefronts.
     */
    private String itemKey(String marketplace, String storefront, String itemId) {
        return marketplace + "|" + storefront + "|" + itemId;
    }

    /**
     * The product's listings that a search returned at or after {@code since} —
     * the result set of one run, rather than everything ever found.
     *
     * <p>Carries the same flags as the Competitors tab, so one row renders
     * identically wherever it appears.</p>
     */
    @Transactional(readOnly = true)
    public List<CompetitorListingResponse> listingsSeenSince(Long productId, Instant since) {
        Product product = tenantProductOrThrow(productId);
        List<CompetitorListing> found = listingRepo
                .findByProductIdAndLastSeenAtGreaterThanEqualOrderByLastPriceAsc(productId, since);
        List<CompetitorListingResponse> responses =
                found.stream().map(l -> toListingResponse(l, product)).toList();
        markPriceHistory(found, responses);
        return responses;
    }

    /**
     * Adopts competitor listings another client already fetched for this same
     * product. Returns how many were adopted; zero when nobody has researched
     * it yet, which is when a real search is warranted.
     */
    private int adoptSharedResearch(Product product) {
        String query = queryFor(product);
        if (query == null || query.isBlank()) {
            return 0;
        }
        int adopted = 0;
        for (Marketplace mp : DEFAULT_MARKETS) {
            // ignoreTtl: existing research is worth showing whatever its age.
            var cached = searchCache.lookup(mp, query, true);
            if (cached.isEmpty()) {
                continue;
            }
            for (CompetitorListing source : cached.get().listings()) {
                copyToProduct(product, mp, source);
                adopted++;
            }
        }
        if (adopted > 0) {
            log.info("Product {} adopted {} shared competitor listing(s) for '{}' — no API call",
                    product.getId(), adopted, query);
        }
        return adopted;
    }

    /**
     * Tenant-wide list of candidates awaiting review (Match Review page), each
     * scored with confidence + signals. Optionally narrowed to one product.
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<CompetitorListingResponse> pendingReview(
            Long productId, org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Page<CompetitorListing> page =
                listingRepo.findForReview(MatchStatus.CANDIDATE, currentTenantScope(), productId, pageable);
        java.util.Set<Long> ids = page.getContent().stream()
                .map(CompetitorListing::getProductId).collect(java.util.stream.Collectors.toSet());
        java.util.Map<Long, Product> products = new java.util.HashMap<>();
        if (!ids.isEmpty()) {
            productRepo.findAllById(ids).forEach(p -> products.put(p.getId(), p));
        }
        return page.map(l -> toListingResponse(l, products.get(l.getProductId())));
    }

    /**
     * Tenant-wide competitor listings for the Competitor Listings page.
     * Paginated + optionally filtered by marketplace/condition/matchStatus/search.
     * Each row is enriched with its product's title (resolved in one batch query).
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<CompetitorListingResponse> listAll(
            String marketplace, String condition, MatchStatus matchStatus, String search,
            org.springframework.data.domain.Pageable pageable) {
        String needle = (search == null || search.isBlank()) ? null : search.trim().toLowerCase();
        String mp = (marketplace == null || marketplace.isBlank()) ? null : marketplace.trim().toUpperCase();
        String cond = (condition == null || condition.isBlank()) ? null : condition.trim();

        org.springframework.data.domain.Page<CompetitorListing> page =
                listingRepo.search(currentTenantScope(), mp, cond, matchStatus, needle, pageable);

        // Resolve products for the page in one query, then score each row
        // (so the Competitor Listings page can also show match score/signals).
        java.util.Set<Long> productIds = page.getContent().stream()
                .map(CompetitorListing::getProductId).collect(java.util.stream.Collectors.toSet());
        java.util.Map<Long, Product> products = new java.util.HashMap<>();
        if (!productIds.isEmpty()) {
            productRepo.findAllById(productIds).forEach(p -> products.put(p.getId(), p));
        }
        return page.map(l -> toListingResponse(l, products.get(l.getProductId())));
    }

    /**
     * Attaches a marketplace listing to a product as a competitor, by hand.
     *
     * <p>Search finds competitors by identifier or title, which leaves a gap:
     * someone who has found the right listing themselves — browsing the
     * marketplace, or following a link — has no way to record it. The listing is
     * real and the judgement is theirs; refusing it because our search did not
     * surface it would be perverse.</p>
     *
     * <p>Fetched live rather than trusted from the request: the caller supplies
     * an item id, and the price, title and seller come from the marketplace. A
     * price typed by hand would be indistinguishable from an observed one
     * afterwards, and every statistic downstream assumes observation.</p>
     *
     * <p>Saved as a CANDIDATE, not a confirmed match — attaching is "look at
     * this", not "this is the same product". The reviewer still decides, and
     * the pack and condition rules still apply when they do.</p>
     */
    @Transactional
    public CompetitorListingResponse attachListing(Long productId, Marketplace marketplace,
            String marketplaceItemId) {
        return attachListing(productId, marketplace, marketplaceItemId, null);
    }

    /**
     * @param region the storefront the listing was found in ({@code US},
     *               {@code CA}, {@code GB}). Pass it whenever it is known — from a
     *               search result's {@code storefront}. Without it the listing is
     *               fetched from whichever storefront answers first with a price,
     *               which stored amazon.com's US$144.29 for a listing the user had
     *               just seen on amazon.ca at CA$140.
     */
    @Transactional
    public CompetitorListingResponse attachListing(Long productId, Marketplace marketplace,
            String marketplaceItemId, String region) {
        Product product = tenantProductOrThrow(productId);
        if (marketplaceItemId == null || marketplaceItemId.isBlank()) {
            throw new BadRequestException("A marketplace item id is required.");
        }
        String itemId = marketplaceItemId.trim();
        String requested = com.priceintel.backend.utils.Storefront.normalise(region);
        if (region != null && !region.isBlank() && requested == null) {
            throw new BadRequestException("Unknown region: " + region
                    + ". Use a two-letter country code such as US, CA or GB.");
        }

        if (requested != null) {
            // Known storefront: the duplicate check can run before spending any
            // API calls.
            var existing = listingRepo.findByProductIdAndMarketplaceAndStorefrontAndMarketplaceItemId(
                    productId, marketplace.name(), requested, itemId);
            if (existing.isPresent()) {
                // Already attached — return it rather than failing. Clicking twice
                // should leave one listing, not an error.
                return toListingResponse(existing.get(), product);
            }
        }

        // Pinned to the requested storefront when there is one. Title, URL and
        // price then describe the same shop; fetched region-less, the listing and
        // the price hunted separately and could each land somewhere different.
        var details = marketplaceService.getListing(marketplace, itemId, requested);
        String storefront = requested != null ? requested
                : com.priceintel.backend.utils.Storefront.resolve(
                        details.getCountryCode(), details.getUrl(), details.getCurrency());
        var price = marketplaceService.getPrice(marketplace, itemId, storefront);

        if (requested == null) {
            // Storefront only known now, after the lookup told us where it landed.
            var existing = listingRepo.findByProductIdAndMarketplaceAndStorefrontAndMarketplaceItemId(
                    productId, marketplace.name(), storefront, itemId);
            if (existing.isPresent()) {
                return toListingResponse(existing.get(), product);
            }
        }

        CompetitorListing listing = CompetitorListing.builder()
                .productId(productId)
                .tenantId(product.getTenantId())
                .marketplace(marketplace.name())
                .storefront(storefront)
                .marketplaceItemId(itemId)
                .matchStatus(MatchStatus.CANDIDATE)
                .title(details.getTitle())
                .url(details.getUrl())
                .seller(details.getSeller())
                .condition(details.getCondition())
                .availability(details.isAvailable() ? "IN_STOCK" : "OUT_OF_STOCK")
                .rating(details.getRating())
                .build();

        Instant now = Instant.now();
        if (price != null) {
            listing.setLastPrice(price.getItemPrice());
            listing.setShipping(price.getShipping());
            listing.setCurrency(price.getCurrency());
        }
        listing.setSourceTimestamp(now);
        listing.setLastFetchedAt(now);
        listing.setLastSeenAt(now);
        listing.setMatchScore(scorer.assess(product, listing).getScore());
        listing = listingRepo.save(listing);

        if (listing.getLastPrice() != null) {
            snapshotRepo.save(ListingPriceSnapshot.builder()
                    .listingId(listing.getId())
                    .marketplace(marketplace.name())
                    .storefront(storefront)
                    .marketplaceItemId(itemId)
                    .itemPrice(listing.getLastPrice())
                    .shipping(listing.getShipping())
                    .landedPrice(landedPrice(listing.getLastPrice(), listing.getShipping()))
                    .currency(listing.getCurrency())
                    .observedAt(now)
                    .build());
        }
        log.info("Attached {} {} {} to product {} as a candidate",
                marketplace, storefront, itemId, productId);
        return toListingResponse(listing, product);
    }

    @Transactional
    public CompetitorListingResponse review(Long listingId, MatchStatus decision) {
        CompetitorListing listing = tenantListingOrThrow(listingId);

        // FR-MATCH-001: hard conflicts prevent an unsafe match being confirmed.
        // Rejecting one is always allowed — the reviewer is agreeing with the
        // rule, not overriding it.
        if (decision == MatchStatus.MATCHED || decision == MatchStatus.EQUIVALENT) {
            Product product = productRepo.findById(listing.getProductId()).orElse(null);
            if (product != null) {
                MatchScoringService.MatchAssessment assessment = scorer.assess(product, listing);
                if (assessment.isBlocked()) {
                    throw new BadRequestException(
                            "This listing cannot be confirmed as a match. "
                                    + assessment.getBlockedReason());
                }
            }
        }
        listing.setMatchStatus(decision);
        return toListingResponse(listingRepo.save(listing));
    }

    // ---------- market prices ----------

    @Transactional
    public MarketPricesResponse marketPrices(Long productId, boolean matchedOnly) {
        // The stat cards and the Competitors tab load together and in no fixed
        // order, so this adopts shared research too — otherwise whichever call
        // lands first would report an empty market.
        Product product = tenantProductOrThrow(productId);
        if (listingRepo.countByProductId(productId) == 0) {
            adoptSharedResearch(product);
        }
        List<CompetitorListing> listings = listingRepo.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .filter(l -> l.getLastPrice() != null)
                // A rejected listing is excluded whatever the mode. Rejecting is
                // the user stating this is not their product; continuing to
                // price against it makes the decision pointless and the
                // statistics wrong — a $21 door shelf dragging the "lowest
                // competitor" for a $2,000 refrigerator, long after someone said
                // it was not one.
                .filter(l -> l.getMatchStatus() != MatchStatus.REJECTED)
                .filter(l -> !matchedOnly
                        || l.getMatchStatus() == MatchStatus.MATCHED
                        || l.getMatchStatus() == MatchStatus.EQUIVALENT)
                .toList();
        // One currency for the whole comparison. The client's reporting currency
        // where they have set one; otherwise the currency most of the listings
        // are already in, which needs no rates and changes nothing for the
        // single-country case.
        String reporting = fxRates.reportingCurrency();
        if (reporting == null) {
            reporting = dominantCurrency(listings);
        }

        // Compare on landed price — item + shipping — so the "lowest competitor"
        // is the one a buyer would actually pay least for, not the one with the
        // lowest headline price and the highest delivery charge.
        //
        // Converted into one currency first. Sorting C$95 next to $70 produced a
        // median that looked authoritative, meant nothing, and fed the
        // recommendation engine — so a listing that cannot be converted is
        // dropped and counted rather than quietly included.
        List<BigDecimal> prices = new ArrayList<>();
        java.util.Set<String> missingRates = new java.util.LinkedHashSet<>();
        boolean anyConverted = false;
        for (CompetitorListing l : listings) {
            BigDecimal landed = landedPrice(l.getLastPrice(), l.getShipping());
            FxRateService.Converted c = fxRates.convert(landed, l.getCurrency(), reporting);
            if (c.isUnavailable()) {
                missingRates.add(l.getCurrency());
                continue;
            }
            anyConverted |= c.wasConverted();
            prices.add(c.amount());
        }
        int excludedForCurrency = listings.size() - prices.size();
        if (excludedForCurrency > 0) {
            log.info("Product {}: {} listing(s) left out of the market statistics — no rate "
                    + "from {} to {}", productId, excludedForCurrency, missingRates, reporting);
        }
        prices.sort(BigDecimal::compareTo);

        // Freshness of the statistics is the freshness of the newest price
        // behind them: an average of week-old observations is not a current
        // average, whatever the arithmetic says.
        Instant newestObservation = listings.stream()
                .map(CompetitorListing::getLastFetchedAt)
                .filter(java.util.Objects::nonNull)
                .max(Instant::compareTo).orElse(null);

        MarketPricesResponse.MarketPricesResponseBuilder b = MarketPricesResponse.builder()
                .productId(productId).matchedOnly(matchedOnly).competitorCount(prices.size())
                .currency(reporting)
                .converted(anyConverted)
                .excludedForCurrency(excludedForCurrency)
                .missingRatesFor(List.copyOf(missingRates))
                .observedAt(newestObservation);
        if (!prices.isEmpty()) {
            b.lowest(prices.get(0)).highest(prices.get(prices.size() - 1))
             .average(average(prices)).median(median(prices));
        }
        MarketPricesResponse response = b.build();
        applyMarketRank(response, product, listings, prices);

        ValueStatus inputs = statusResolver.forObservation(
                prices.isEmpty() ? null : prices.get(0), newestObservation);
        response.setValueStatus(Map.of(
                "lowest",  statusResolver.forCalculation(response.getLowest(), inputs).name(),
                "highest", statusResolver.forCalculation(response.getHighest(), inputs).name(),
                "average", statusResolver.forCalculation(response.getAverage(), inputs).name(),
                "median",  statusResolver.forCalculation(response.getMedian(), inputs).name()));
        return response;
    }

    /**
     * The currency most of these listings are already priced in.
     *
     * <p>Used only when the client has not set a reporting currency. It keeps
     * the single-country case working with no rates configured at all, and
     * converts the minority rather than the majority — fewer conversions means
     * fewer figures depending on a rate being present and current.</p>
     */
    private String dominantCurrency(List<CompetitorListing> listings) {
        return listings.stream()
                .map(CompetitorListing::getCurrency)
                .filter(c -> c != null && !c.isBlank())
                .collect(java.util.stream.Collectors.groupingBy(
                        c -> c.toUpperCase(), java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .max(java.util.Map.Entry.comparingByValue())
                .map(java.util.Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * Places our price among the competing offers (FR-PRICE-004).
     *
     * <p>Rank answers the question the four statistic cards do not: lowest,
     * median and highest describe the market, but not where we stand in it.
     * "3rd of 14" is actionable in a way that "$70 against a $74.99 median" is
     * not.</p>
     *
     * <p>Ties share a rank — two sellers at the same price are jointly cheapest,
     * and the loser of an arbitrary tiebreak would appear to be undercut when
     * nobody has undercut them.</p>
     */
    private void applyMarketRank(MarketPricesResponse response, Product product,
            List<CompetitorListing> listings, List<BigDecimal> sortedPrices) {
        BigDecimal ourPrice = product.getOurPrice();
        response.setOurPrice(ourPrice);
        if (ourPrice == null || sortedPrices.isEmpty()) {
            // No price of ours, or nothing to compare against. Reporting rank 1
            // here would read as market-leading rather than unknown.
            return;
        }
        // Cheapest first: how many offers are strictly below ours.
        long cheaper = sortedPrices.stream().filter(p -> p.compareTo(ourPrice) < 0).count();

        response.setMarketRank((int) cheaper + 1);
        response.setMarketRankTotal(sortedPrices.size() + 1);
        response.setPriceGapToLowest(ourPrice.subtract(sortedPrices.get(0)));

        long confirmed = listings.stream()
                .filter(l -> l.getMatchStatus() == MatchStatus.MATCHED
                        || l.getMatchStatus() == MatchStatus.EQUIVALENT)
                .count();
        response.setMarketRankBasis(confirmed == listings.size() ? "CONFIRMED"
                : confirmed == 0 ? "UNREVIEWED" : "MIXED");
    }

    // ---------- price history ----------

    /**
     * Price history for a listing, read by the real-world marketplace item
     * rather than by this row's id. Every observation of that item counts,
     * whichever product or client triggered the fetch, so the series is one
     * dense history instead of a thin one per tenant.
     */
    @Transactional(readOnly = true)
    public List<ListingPriceSnapshot> priceHistory(Long listingId, Instant from, Instant to) {
        CompetitorListing listing = tenantListingOrThrow(listingId);

        String mp = listing.getMarketplace();
        String itemId = listing.getMarketplaceItemId();
        if (itemId == null || itemId.isBlank()) {
            // Pre-backfill row: fall back to the per-row history.
            return from != null && to != null
                    ? snapshotRepo.findByListingIdAndObservedAtBetweenOrderByObservedAtAsc(listingId, from, to)
                    : snapshotRepo.findByListingIdOrderByObservedAtAsc(listingId);
        }
        // This storefront's series only, so a Canadian listing's chart is drawn in
        // Canadian dollars and never zig-zags into the American copy's prices.
        String storefront = listing.getStorefront();
        return from != null && to != null
                ? snapshotRepo
                    .findByMarketplaceAndStorefrontAndMarketplaceItemIdAndObservedAtBetweenOrderByObservedAtAsc(
                            mp, storefront, itemId, from, to)
                : snapshotRepo.findByMarketplaceAndStorefrontAndMarketplaceItemIdOrderByObservedAtAsc(
                        mp, storefront, itemId);
    }

    /**
     * Product-level rolled-up price history for the Price Intelligence chart:
     * competitor lowest/highest/median/average per time bucket (day or week),
     * plus our current price as a reference line. Aggregates the per-listing
     * snapshots across all of the product's competitor listings.
     */
    @Transactional(readOnly = true)
    public List<com.priceintel.backend.dto.response.PriceHistoryPoint> priceHistoryRollup(
            Long productId, Instant from, Instant to, String bucket) {
        Product product = tenantProductOrThrow(productId);
        // Rejected listings are left out of the chart for the same reason they
        // are left out of the statistics: they are not this product.
        List<CompetitorListing> productListings =
                listingRepo.findByProductIdOrderByCreatedAtDesc(productId).stream()
                        .filter(l -> l.getMatchStatus() != MatchStatus.REJECTED)
                        .toList();
        if (productListings.isEmpty()) {
            return List.of();
        }
        Instant end = to != null ? to : Instant.now();
        Instant start = from != null ? from : end.minus(java.time.Duration.ofDays(90));
        // A boolean could only say day-or-week, so "month" silently produced a
        // daily chart — an unrecognised bucket answering a question nobody asked.
        String grain = bucket == null ? "day" : bucket.trim().toLowerCase();
        if (!grain.equals("day") && !grain.equals("week") && !grain.equals("month")) {
            throw new BadRequestException(
                    "Unknown bucket: " + bucket + ". Use day, week or month.");
        }

        // Roll up by real-world item so shared observations are included.
        List<String> itemIds = productListings.stream()
                .map(CompetitorListing::getMarketplaceItemId)
                .filter(id -> id != null && !id.isBlank())
                .distinct().toList();
        List<ListingPriceSnapshot> snaps;
        if (itemIds.isEmpty()) {
            snaps = snapshotRepo.findByListingIdInAndObservedAtBetweenOrderByObservedAtAsc(
                    productListings.stream().map(CompetitorListing::getId).toList(), start, end);
        } else {
            // The query matches item ids only, which also returns other shops'
            // copies of the same ASIN — ones this product never attached. Keep
            // exactly the listings that are this product's competitors.
            Set<String> keys = productListings.stream()
                    .filter(l -> l.getMarketplaceItemId() != null)
                    .map(l -> itemKey(l.getMarketplace(), l.getStorefront(), l.getMarketplaceItemId()))
                    .collect(java.util.stream.Collectors.toSet());
            snaps = snapshotRepo.findByItemIdsBetween(itemIds, start, end).stream()
                    .filter(s -> keys.contains(
                            itemKey(s.getMarketplace(), s.getStorefront(), s.getMarketplaceItemId())))
                    .toList();
        }

        // Group snapshots by bucket start date (UTC).
        java.util.Map<java.time.LocalDate, List<ListingPriceSnapshot>> byBucket =
                new java.util.TreeMap<>();
        for (ListingPriceSnapshot s : snaps) {
            java.time.LocalDate d = s.getObservedAt().atZone(ZoneOffset.UTC).toLocalDate();
            d = switch (grain) {
                case "week" -> d.with(java.time.temporal.TemporalAdjusters
                        .previousOrSame(java.time.DayOfWeek.MONDAY));
                case "month" -> d.withDayOfMonth(1);
                default -> d;
            };
            byBucket.computeIfAbsent(d, k -> new ArrayList<>()).add(s);
        }

        List<com.priceintel.backend.dto.response.PriceHistoryPoint> points = new ArrayList<>();
        for (var e : byBucket.entrySet()) {
            List<BigDecimal> prices = new ArrayList<>();
            // Count distinct real-world listings, not rows: the same ASIN tracked
            // by two clients is one competitor, not two.
            java.util.Set<String> listings = new java.util.HashSet<>();
            for (ListingPriceSnapshot s : e.getValue()) {
                BigDecimal p = s.getLandedPrice() != null ? s.getLandedPrice() : s.getItemPrice();
                if (p != null) {
                    prices.add(p);
                    listings.add(s.getMarketplaceItemId() != null
                            ? itemKey(s.getMarketplace(), s.getStorefront(), s.getMarketplaceItemId())
                            : "row:" + s.getListingId());
                }
            }
            if (prices.isEmpty()) {
                continue;
            }
            prices.sort(BigDecimal::compareTo);
            BigDecimal lowest = prices.get(0);
            BigDecimal highest = prices.get(prices.size() - 1);
            points.add(com.priceintel.backend.dto.response.PriceHistoryPoint.builder()
                    .period(e.getKey())
                    .lowest(lowest).highest(highest)
                    .median(median(prices)).average(average(prices))
                    .ourPrice(product.getOurPrice())
                    .competitorCount(listings.size())
                    .volatilityPct(volatility(prices))
                    .priceRange(highest.subtract(lowest))
                    .build());
        }
        return points;
    }

    // ---------- helpers ----------

    private List<Marketplace> resolveMarkets(List<String> markets) {
        if (markets == null || markets.isEmpty()) {
            return DEFAULT_MARKETS;
        }
        List<Marketplace> out = new ArrayList<>();
        for (String m : markets) {
            try {
                out.add(Marketplace.valueOf(m.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Unknown marketplace: " + m);
            }
        }
        return out;
    }

    /**
     * Preference order for identifiers: an ASIN addresses the exact Amazon
     * listing, then the global barcodes, then the manufacturer part number.
     */
    private static final List<IdentifierType> QUERY_IDENTIFIER_PRIORITY = List.of(
            IdentifierType.ASIN, IdentifierType.UPC, IdentifierType.EAN,
            IdentifierType.GTIN, IdentifierType.MPN);

    /**
     * Builds the marketplace search term for a product.
     *
     * <p>An identifier is preferred over the title for two reasons. It finds the
     * actual product instead of whatever a keyword search surfaces — a title
     * search returns accessories and spare parts, which is why title-derived
     * matches score so poorly. And because an identifier is the same string for
     * every client, two clients tracking the same product produce the same
     * cache key, so the second one is served without an API call.</p>
     *
     * <p>Falls back to the title, then brand + SKU, when no identifier exists.</p>
     */
    private String queryFor(Product product) {
        Optional<String> identifier = QUERY_IDENTIFIER_PRIORITY.stream()
                .map(type -> product.getIdentifiers().stream()
                        .filter(i -> i.getType() == type)
                        .map(i -> i.getNormalizedValue() != null && !i.getNormalizedValue().isBlank()
                                ? i.getNormalizedValue() : i.getOriginalValue())
                        .filter(v -> v != null && !v.isBlank())
                        .findFirst())
                .flatMap(Optional::stream)
                .findFirst();
        if (identifier.isPresent()) {
            return identifier.get();
        }
        return titleFor(product);
    }

    /**
     * The descriptive search term for a product, ignoring identifiers.
     *
     * <p>Used as the fallback when an identifier finds nothing. Falls back
     * further to brand + SKU for products with no title at all.</p>
     */
    private String titleFor(Product product) {
        String q = product.getTitle();
        if (q == null || q.isBlank()) {
            q = product.getBrand() != null ? product.getBrand() + " " + product.getSku() : product.getSku();
        }
        return q;
    }

    /**
     * Item price plus shipping — the figure a buyer actually pays.
     *
     * <p>Unknown shipping is left out rather than assumed to be zero: treating
     * "we don't know" as "free" would understate the true cost and make the
     * listing look cheaper than it is.</p>
     */
    private BigDecimal landedPrice(BigDecimal itemPrice, BigDecimal shipping) {
        if (itemPrice == null) {
            return null;
        }
        return shipping == null ? itemPrice : itemPrice.add(shipping);
    }

    private BigDecimal average(List<BigDecimal> prices) {
        BigDecimal sum = prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(prices.size()), 2, RoundingMode.HALF_UP);
    }

    /**
     * How far the prices in a bucket spread, as a percentage of their average
     * (FR-PRICE-003).
     *
     * <p>The coefficient of variation, not the raw standard deviation, so the
     * figure means the same thing on a £400 monitor as on a £4 cable — a £10
     * spread is noise on one and turmoil on the other.</p>
     *
     * @return null with fewer than two prices. One observation has no spread,
     *         and returning 0 would assert a stability nothing measured
     */
    private BigDecimal volatility(List<BigDecimal> prices) {
        if (prices == null || prices.size() < 2) {
            return null;
        }
        BigDecimal mean = average(prices);
        if (mean == null || mean.signum() == 0) {
            return null;
        }
        double m = mean.doubleValue();
        double sumSquares = 0;
        for (BigDecimal p : prices) {
            double diff = p.doubleValue() - m;
            sumSquares += diff * diff;
        }
        // Population standard deviation: these are all the prices observed in
        // the bucket, not a sample drawn from a larger set.
        double stdDev = Math.sqrt(sumSquares / prices.size());
        return BigDecimal.valueOf(stdDev * 100 / m).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal median(List<BigDecimal> sorted) {
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }
        return sorted.get(n / 2 - 1).add(sorted.get(n / 2))
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    private SearchJobResponse toJobResponse(SearchJob j) {
        return SearchJobResponse.builder()
                .id(j.getId()).productId(j.getProductId()).markets(j.getMarkets())
                .status(j.getStatus()).resultCount(j.getResultCount())
                .correlationId(j.getCorrelationId()).finishedAt(j.getFinishedAt()).note(j.getNote())
                .build();
    }

    private CompetitorListingResponse toListingResponse(CompetitorListing l) {
        return toListingResponse(l, (String) null);
    }

    /** Mapping without scoring (used by the Competitor Listings page). */
    private CompetitorListingResponse toListingResponse(CompetitorListing l, String productTitle) {
        return baseResponse(l).productTitle(productTitle).build();
    }

    /** Mapping WITH match scoring (used by candidates + Match Review). */
    private CompetitorListingResponse toListingResponse(CompetitorListing l, Product product) {
        CompetitorListingResponse.CompetitorListingResponseBuilder b = baseResponse(l);
        if (product != null) {
            b.productTitle(product.getTitle());
            MatchScoringService.MatchAssessment a = scorer.assess(product, l);
            b.matchScore(a.getScore()).matchSignals(a.getSignals())
             .blockedReason(a.getBlockedReason())
             .reasons(a.getSignals().stream().filter(MatchSignal::isPositive)
                     .map(MatchSignal::getLabel).toList())
             .conflicts(a.getSignals().stream().filter(s -> !s.isPositive())
                     .map(MatchSignal::getLabel).toList());
        }
        return b.build();
    }

    /**
     * Works out what this listing's price would earn us, and says which costs it
     * used.
     *
     * <p>Deliberately reuses {@link CostProfileService#computeProfitability},
     * rather than recomputing margin here, so this figure and the Costs &amp;
     * Profit screen can never drift apart when fee handling changes.</p>
     *
     * <p>Silently returns nothing rather than a wrong number in three cases: no
     * price to work from, no costs configured, or a listing priced in a
     * different currency from the costs — the region fallback can return CAD or
     * GBP listings, and there is no FX conversion, so a cross-currency margin
     * would be arithmetic on unlike units.</p>
     */
    private void applyMargin(CompetitorListingResponse.CompetitorListingResponseBuilder b,
                             CompetitorListing l) {
        BigDecimal landed = landedPrice(l.getLastPrice(), l.getShipping());
        if (landed == null || landed.signum() <= 0) {
            return;
        }
        try {
            ProfitabilityResponse p = costProfileService.computeProfitability(l.getProductId(), landed);
            if (!p.isCostProfileConfigured()) {
                return; // null basis → the UI prompts for costs instead of showing 0%
            }
            if (l.getCurrency() != null && p.getCurrency() != null
                    && !l.getCurrency().equalsIgnoreCase(p.getCurrency())) {
                b.marginBasis("CURRENCY_MISMATCH");
                return;
            }
            b.estimatedMargin(p.getNetProfitEstimate())
             .estimatedMarginPct(p.getContributionMarginPct())
             .marginBasis("PRODUCT".equals(p.getCostSource()) ? "PRODUCT_PROFILE" : p.getCostSource());
        } catch (RuntimeException e) {
            // A missing product or unusable price is not a reason to fail the
            // whole listing row — the margin column simply stays empty.
            log.debug("No margin for listing {}: {}", l.getId(), e.getMessage());
        }
    }

    private CompetitorListingResponse.CompetitorListingResponseBuilder baseResponse(CompetitorListing l) {
        CompetitorListingResponse.CompetitorListingResponseBuilder b = CompetitorListingResponse.builder()
                .id(l.getId()).productId(l.getProductId())
                .marketplace(l.getMarketplace())
                .storefront(l.getStorefront())
                .marketplaceItemId(l.getMarketplaceItemId()).title(l.getTitle())
                // Rows stored before the connectors recorded a URL have none;
                // rebuild one so every listing on screen is checkable, not just
                // the ones fetched since — on its own storefront, so a Canadian
                // listing's link opens amazon.ca.
                .url(l.getUrl() != null ? l.getUrl()
                        : com.priceintel.backend.utils.MarketplaceUrlParser.build(
                                l.getMarketplace(), l.getMarketplaceItemId(), l.getStorefront()))
                .seller(l.getSeller()).condition(l.getCondition()).currency(l.getCurrency())
                .matchStatus(l.getMatchStatus().name()).lastPrice(l.getLastPrice())
                .shipping(l.getShipping()).availability(l.getAvailability()).rating(l.getRating())
                .sourceTimestamp(l.getSourceTimestamp())
                .lastFetchedAt(l.getLastFetchedAt())
                .lastSeenAt(l.getLastSeenAt())
                .landedPrice(landedPrice(l.getLastPrice(), l.getShipping()));
        applyMargin(b, l);
        applyValueStatus(b, l);
        return b;
    }

    /**
     * Labels each number on the row with how far it can be trusted
     * (FR-REPORT-001), so a price read an hour ago and one read last week are
     * not presented identically.
     */
    private void applyValueStatus(CompetitorListingResponse.CompetitorListingResponseBuilder b,
                                  CompetitorListing l) {
        CompetitorListingResponse partial = b.build();
        ValueStatus priceStatus = statusResolver.forObservation(l.getLastPrice(), l.getLastFetchedAt());

        // A margin built on tenant-wide costs is an estimate for this product,
        // however precise the arithmetic; one built on the product's own costs
        // is a calculation. Both inherit the price's staleness.
        ValueStatus marginStatus;
        if (partial.getEstimatedMargin() == null) {
            marginStatus = ValueStatus.UNAVAILABLE;
        } else if ("TENANT_DEFAULT".equals(partial.getMarginBasis())) {
            marginStatus = priceStatus == ValueStatus.STALE
                    ? ValueStatus.STALE : ValueStatus.ESTIMATED;
        } else {
            marginStatus = statusResolver.forCalculation(partial.getEstimatedMargin(), priceStatus);
        }

        // Shipping is unknown far more often than not — 192 of 200 priced
        // listings carry no delivery cost, because Amazon's competitivePrice
        // does not report one and no destination was ever supplied.
        ValueStatus shippingStatus = l.getShipping() == null
                ? ValueStatus.UNAVAILABLE
                : statusResolver.forObservation(l.getShipping(), l.getLastFetchedAt());

        // A landed price computed without shipping is a floor, not a total, so
        // it must not claim to be one. Previously it inherited only the item
        // price's status and so read as a complete figure — treating "delivery
        // unknown" as "delivery free", which understates every such competitor
        // by exactly their postage and skews lowest/median/rank against us.
        ValueStatus landedStatus;
        if (partial.getLandedPrice() == null) {
            landedStatus = ValueStatus.UNAVAILABLE;
        } else if (l.getShipping() == null) {
            landedStatus = priceStatus == ValueStatus.STALE
                    ? ValueStatus.STALE : ValueStatus.ESTIMATED;
        } else {
            landedStatus = statusResolver.forCalculation(partial.getLandedPrice(), priceStatus);
        }

        b.valueStatus(Map.of(
                "lastPrice", priceStatus.name(),
                "shipping", shippingStatus.name(),
                "landedPrice", landedStatus.name(),
                "estimatedMargin", marginStatus.name()));
    }

    // static import helper for LocalDateTime→Instant if needed later
    @SuppressWarnings("unused")
    private static Instant toInstant(java.time.LocalDateTime dt) {
        return dt == null ? null : dt.toInstant(ZoneOffset.UTC);
    }
}
