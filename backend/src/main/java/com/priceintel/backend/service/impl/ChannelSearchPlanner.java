package com.priceintel.backend.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.priceintel.backend.dto.request.ChannelSearchRequest;
import com.priceintel.backend.dto.request.MarketplaceSearchRequest;
import com.priceintel.backend.dto.response.ChannelSearchResponse;
import com.priceintel.backend.dto.response.ChannelSearchResponse.SearchStep;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.exception.MarketplaceApiException;
import com.priceintel.backend.marketplace.Marketplace;
import com.priceintel.backend.marketplace.model.SearchResult;
import com.priceintel.backend.marketplace.model.SearchResultItem;
import com.priceintel.backend.service.MarketplaceService;
import com.priceintel.backend.utils.MarketplaceUrlParser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs an ad-hoc marketplace search from whatever the caller knows about a
 * product (FR-SRCH-001 to 003).
 *
 * <p>The ordering is the whole point. An identifier names one listing; a title
 * describes a shelf. Trying them in the wrong order returns plausible neighbours
 * — a door shelf instead of a refrigerator — and nothing downstream can tell
 * the difference afterwards. So exact keys are exhausted before anything
 * broader, and the answer records which one actually produced the results.</p>
 *
 * <p>This lives on the server rather than in the client because it is a policy,
 * not a screen: the same waterfall has to hold for the scheduled monitors and
 * for any future caller, and a copy in the browser would drift from it.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelSearchPlanner {

    /**
     * Identifier types in the order they are tried, most precise first.
     *
     * <p>SKU is absent on purpose: it is the seller's private code, so a
     * marketplace has no way to resolve it. Accepting it and silently searching
     * it as keywords would return whatever happened to mention the string.</p>
     */
    /**
     * Identifier types to try, most specific first.
     *
     * <p>{@code ITEM_ID} leads because it is not a search at all — it names one
     * listing on one marketplace and is fetched directly. {@code MARKETPLACE_ID}
     * is the same thing under the name the URL parser emits, kept so a pasted
     * eBay link works; without it those links resolved to a type nothing in this
     * list matched, and the search silently did nothing.</p>
     */
    private static final List<String> IDENTIFIER_PRIORITY =
            List.of("ITEM_ID", "MARKETPLACE_ID", "ASIN", "UPC", "EAN", "GTIN", "MPN");

    /** Types naming one listing directly, rather than something to search for. */
    private static final java.util.Set<String> DIRECT_LOOKUP_TYPES =
            java.util.Set.of("ITEM_ID", "MARKETPLACE_ID");

    /**
     * Key under which an image-derived query travels with the identifiers.
     *
     * <p>Not an identifier type, and never sent to a marketplace as one — it is
     * search words, and the prefix keeps it from ever colliding with a real
     * type name.</p>
     */
    private static final String IMAGE_QUERY_KEY = "__IMAGE_QUERY";

    private final MarketplaceService marketplaceService;
    private final ProductImageSearchService imageSearchService;
    private final ImageDescriptionService imageDescriptionService;
    private final ListingJudgeService judgeService;
    private final MarketplaceItemService marketplaceItems;

    public ChannelSearchResponse search(ChannelSearchRequest request) {
        if (request == null || !request.hasAnyInput()) {
            throw new BadRequestException(
                    "Provide at least one of: an identifier, a title, a product URL, or an image.");
        }
        String correlationId = UUID.randomUUID().toString();
        Instant startedAt = Instant.now();
        List<SearchStep> steps = new ArrayList<>();

        Map<String, String> identifiers = resolveIdentifiers(request, steps);
        List<Marketplace> markets = resolveMarkets(request);
        int limit = request.getMaxResults() != null ? request.getMaxResults() : 10;

        // Attempts in priority order: every identifier we hold, then the title.
        List<Attempt> attempts = new ArrayList<>();
        for (String type : IDENTIFIER_PRIORITY) {
            String value = identifiers.get(type);
            if (value != null && !value.isBlank()) {
                attempts.add(new Attempt(value.trim(), type));
            }
        }
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            // Brand qualifies the title: "MX Master 3S" alone matches accessories
            // for it as readily as the mouse itself.
            String titleQuery = request.getBrand() != null && !request.getBrand().isBlank()
                    && !request.getTitle().toLowerCase().contains(request.getBrand().toLowerCase())
                    ? request.getBrand().trim() + " " + request.getTitle().trim()
                    : request.getTitle().trim();
            attempts.add(new Attempt(titleQuery, null));
        }
        // Whatever the image produced — a catalogue product's title, or a
        // model's description of the photograph — searched as keywords, and
        // last, because it is the least precise thing we hold.
        String imageQuery = identifiers.get(IMAGE_QUERY_KEY);
        if (imageQuery != null && !imageQuery.isBlank()) {
            attempts.add(new Attempt(imageQuery, null));
        }

        for (Attempt attempt : attempts) {
            // Every requested channel is searched, not just until one answers.
            // Stopping at the first made "All connected" a lie: Amazon returned
            // twelve results and eBay was never called, so a whole marketplace
            // was invisible whenever the other one happened to work.
            List<SearchResultItem> items = new ArrayList<>();
            SearchResult firstResult = null;
            Marketplace firstMarket = null;

            List<SearchResult> answers = searchChannels(markets, attempt, limit, request, steps, correlationId);
            for (int m = 0; m < markets.size(); m++) {
                Marketplace mp = markets.get(m);
                SearchResult result = answers.get(m);
                if (result == null || result.getItems().isEmpty()) {
                    continue;
                }
                // Stamp the source, or a merged list cannot say which channel an
                // item came from.
                result.getItems().forEach(i -> i.setMarketplace(mp.name()));
                if (firstResult == null) {
                    firstResult = result;
                    firstMarket = mp;
                }
                items.addAll(result.getItems());
            }
            if (items.isEmpty()) {
                continue;
            }

            // An identifier resolves to exactly the product asked for and
            // nothing else — one ASIN is one page. That is the right answer to
            // "what is this", and the wrong one to "who competes with it". Ask
            // a second question, using the title just returned for the product,
            // and merge the rivals in beside it.
            ChannelSearchRequest effective = request;
            if (attempt.type() != null) {
                effective = enrichFrom(request, items.get(0));
                if (request.isIncludeSimilar()) {
                    items = withSimilar(items, effective, firstMarket, limit, steps, correlationId);
                }
            }

            ChannelSearchResponse.ChannelSearchResponseBuilder builder =
                    ChannelSearchResponse.builder()
                    .correlationId(correlationId)
                    .items(items)
                    .totalResults(items.size())
                    .resolvedQuery(describe(attempt))
                    .resolvedIdentifierType(attempt.type())
                    .steps(steps)
                    .startedAt(startedAt).finishedAt(Instant.now())
                    .note(noteFor(attempt, firstResult, steps));
            applyJudging(builder, effective, items, firstMarket, steps, correlationId);
            return builder.build();
        }

        return ChannelSearchResponse.builder()
                .correlationId(correlationId)
                .items(List.of()).totalResults(0)
                .resolvedQuery(attempts.isEmpty() ? null : describe(attempts.get(0)))
                .resolvedIdentifierType(attempts.isEmpty() ? null : attempts.get(0).type())
                .steps(steps)
                .startedAt(startedAt).finishedAt(Instant.now())
                .note(emptyNote(steps))
                .status(request.isJudge() ? "NO_LISTINGS" : "NOT_JUDGED")
                .message(emptyNote(steps))
                .retryable(steps.stream().anyMatch(s -> "RATE_LIMITED".equals(s.getOutcome())
                        || "PROVIDER_ERROR".equals(s.getOutcome())))
                // Null in both cases: nothing came back, so nothing was judged.
                // An empty array would claim the judge ran and found none.
                .matches(null)
                .rejected(null)
                .build();
    }

    /**
     * Judges the results, when the caller asked for it, and records the step.
     *
     * <p>Part of the same response as the trace rather than a separate call: a
     * caller needs both to act — which listings are the product, and how they
     * were found — and splitting them forced a choice between the two.</p>
     */
    private void applyJudging(ChannelSearchResponse.ChannelSearchResponseBuilder builder,
            ChannelSearchRequest request, List<SearchResultItem> items, Marketplace mp,
            List<SearchStep> steps, String correlationId) {
        if (!request.isJudge()) {
            builder.status("NOT_JUDGED");
            return;
        }
        long start = System.currentTimeMillis();
        var verdicts = judgeService.judge(request, items, mp.name());
        boolean judged = verdicts.judgedByAi();
        boolean notConfigured = ListingJudgeService.NOT_CONFIGURED.equals(
                verdicts.unavailableReason());

        steps.add(SearchStep.builder()
                .marketplace("AI")
                .query("judge " + items.size() + " listing(s)")
                .identifierType("MATCH")
                // SKIPPED and PROVIDER_ERROR are kept apart so the trace answers
                // the first question asked of a failed judge: was it misconfigured
                // or did the provider fail? They need different responses.
                .outcome(judged ? "SUCCESS" : notConfigured ? "SKIPPED" : "PROVIDER_ERROR")
                .resultCount(judged ? verdicts.matches().size() : 0)
                .durationMs(System.currentTimeMillis() - start)
                .detail(judged
                        ? verdicts.matches().size() + " matched, "
                                + verdicts.rejected().size() + " rejected"
                        : notConfigured
                                ? "No AI provider is configured — set openai.enabled "
                                        + "and OPENAI_API_KEY."
                                : verdicts.unavailableReason())
                .build());

        if (judged) {
            log.info("[{}] judged {} listing(s): {} matched, {} rejected",
                    correlationId, items.size(), verdicts.matches().size(),
                    verdicts.rejected().size());
            // Keep the history of what the model accepted. Only the matches:
            // tracking every lookalike a search turns up would fill the table
            // with products nobody is competing against. Rejected listings are
            // still returned to the caller, just not followed over time.
            var recorded = marketplaceItems.record(verdicts.matches());
            if (recorded.created() > 0 || recorded.changed() > 0) {
                log.info("[{}] tracking: {} new item(s), {} price change(s)",
                        correlationId, recorded.created(), recorded.changed());
            }
            // Populated only when the model ruled. Left null otherwise, so a
            // client cannot read an unjudged listing as a confirmed match.
            builder.matches(verdicts.matches()).rejected(verdicts.rejected());
        } else {
            log.warn("[{}] {} listing(s) returned unjudged: {}",
                    correlationId, items.size(), verdicts.unavailableReason());
        }

        builder.status(verdicts.status())
                .message(judgeService.message(verdicts))
                .retryable(verdicts.retryable());
    }

    /**
     * Fills in the title and brand an identifier search just proved, when the
     * caller supplied only a code.
     *
     * <p>Judging compares candidates against a reference product. Given only
     * "asin: B0D7DKJ75M" that reference is a string of characters describing
     * nothing, so every verdict comes back UNCERTAIN with "reference product
     * details are unknown" — which is the model correctly reporting that it was
     * asked to compare against nothing. Amazon has just told us the title and
     * brand for that code; using them turns the reference into a product.</p>
     */
    private ChannelSearchRequest enrichFrom(ChannelSearchRequest request, SearchResultItem item) {
        boolean needsTitle = request.getTitle() == null || request.getTitle().isBlank();
        boolean needsBrand = request.getBrand() == null || request.getBrand().isBlank();
        if ((!needsTitle && !needsBrand) || item == null) {
            return request;
        }
        ChannelSearchRequest copy = ChannelSearchRequest.builder()
                .identifiers(request.getIdentifiers())
                .title(needsTitle ? item.getTitle() : request.getTitle())
                .brand(needsBrand ? item.getSeller() : request.getBrand())
                .url(request.getUrl())
                .image(request.getImage())
                .markets(request.getMarkets())
                .region(request.getRegion())
                .destination(request.getDestination())
                .condition(request.getCondition())
                .maxResults(request.getMaxResults())
                .forceRefresh(request.isForceRefresh())
                .judge(request.isJudge())
                .includeSimilar(request.isIncludeSimilar())
                .requirePrice(request.getRequirePrice())
                .build();
        return copy;
    }

    /**
     * Adds rivals to a single identifier hit, by searching the resolved title.
     *
     * <p>The identifier's own result stays first: it is the product that was
     * actually asked for, and is known exactly rather than inferred. Everything
     * the keyword search adds is a candidate the judge still has to rule on.</p>
     *
     * <p>Best-effort. If the second search fails the first result is returned
     * unchanged — a found product with no rivals beats an error.</p>
     */
    private List<SearchResultItem> withSimilar(List<SearchResultItem> exact,
            ChannelSearchRequest request, Marketplace mp, int limit,
            List<SearchStep> steps, String correlationId) {
        String title = request.getTitle();
        if (title == null || title.isBlank()) {
            return exact;
        }
        try {
            SearchResult similar = runStep(mp, new Attempt(title.trim(), null),
                    limit, request, steps, correlationId);
            if (similar == null || similar.getItems().isEmpty()) {
                return exact;
            }
            // The exact hit appears in the keyword results too; keep one copy,
            // and keep it first.
            java.util.LinkedHashMap<String, SearchResultItem> merged =
                    new java.util.LinkedHashMap<>();
            exact.forEach(i -> merged.put(i.getMarketplaceItemId(), i));
            similar.getItems().forEach(i -> merged.putIfAbsent(i.getMarketplaceItemId(), i));
            log.info("[{}] {} rival(s) added beside the identifier hit",
                    correlationId, merged.size() - exact.size());
            return new ArrayList<>(merged.values());
        } catch (RuntimeException e) {
            log.warn("[{}] could not search for rivals: {}", correlationId, e.getMessage());
            return exact;
        }
    }

    /**
     * Fetches one named listing, rather than searching for it.
     *
     * <p>An item id is the marketplace's own handle for a single listing —
     * eBay's {@code v1|167815729577|0} or the bare number a person copies from
     * a listing page. Searching for it as keywords finds nothing, because the
     * id appears nowhere in the title; the only way to resolve it is to ask for
     * that listing directly.</p>
     *
     * <p>Wrapped as a one-item {@link SearchResult} so the rest of the pipeline
     * — judging, tracking, the trace — is unchanged.</p>
     */
    private SearchResult fetchOne(Marketplace mp, String itemId, String region) {
        // An item id belongs to one marketplace, and the two formats cannot be
        // confused: an ASIN is ten characters, an eBay id is "v1|…" or a long
        // number. Asking the wrong marketplace spends a call to be told no.
        if (!looksLikeIdFor(mp, itemId)) {
            return SearchResult.builder().marketplace(mp).query(itemId)
                    .items(new ArrayList<>()).totalResults(0).build();
        }
        // The storefront the user searched in, exactly as keyword searches honour
        // it. An item-id lookup that ignored it answered a Canadian search with
        // the American listing.
        var details = marketplaceService.getListing(mp, itemId,
                com.priceintel.backend.utils.Storefront.normalise(region));
        if (details == null) {
            return SearchResult.builder().marketplace(mp).query(itemId)
                    .items(new ArrayList<>()).totalResults(0).build();
        }
        var price = details.getPrice();
        SearchResultItem item = SearchResultItem.builder()
                .marketplaceItemId(details.getMarketplaceItemId())
                .title(details.getTitle())
                .url(details.getUrl())
                .seller(details.getSeller())
                .condition(details.getCondition())
                .price(price)
                .currency(details.getCurrency())
                .availability(details.isAvailable() ? "IN_STOCK" : "OUT_OF_STOCK")
                .rating(details.getRating())
                .identifiers(details.getIdentifiers())
                .marketplace(mp.name())
                .storefront(com.priceintel.backend.utils.Storefront.resolve(
                        details.getCountryCode(), details.getUrl(), details.getCurrency()))
                .build();
        return SearchResult.builder()
                .marketplace(mp).query(itemId)
                .items(new ArrayList<>(List.of(item)))
                .totalResults(1)
                .sourceTimestamp(details.getSourceTimestamp())
                .build();
    }

    /**
     * Whether this attempt names one listing rather than describing something
     * to search for. Null-safe: a title attempt has no identifier type.
     */
    private boolean isDirectLookup(String identifierType) {
        return identifierType != null && DIRECT_LOOKUP_TYPES.contains(identifierType);
    }

    /** Whether this looks like an id that marketplace could own. */
    private boolean looksLikeIdFor(Marketplace mp, String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        String v = id.trim();
        return switch (mp) {
            // An ASIN is exactly ten alphanumerics.
            case AMAZON -> v.matches("(?i)[A-Z0-9]{10}");
            // eBay uses "v1|<item>|<variation>" or a bare 9-15 digit legacy id.
            case EBAY -> v.startsWith("v1|") || v.matches("\\d{9,15}");
        };
    }

    // ---------- all channels, at once ----------

    /** One channel's answer and the steps it recorded, kept apart until joined. */
    private record ChannelOutcome(SearchResult result, List<SearchStep> steps) {
    }

    /**
     * Every requested channel, searched side by side.
     *
     * <p>They were searched one after another, so eBay did not start until
     * Amazon's catalogue and pricing calls had both finished: 2.8s of Amazon,
     * then 1.6s of eBay, when neither needs anything from the other. Together
     * the wait is the slower of the two rather than their sum.</p>
     *
     * <p>Results come back in the order the channels were asked for, and each
     * channel records its steps into its own list, appended afterwards in that
     * order — so the response reads the same as before, just sooner.</p>
     */
    private List<SearchResult> searchChannels(List<Marketplace> markets, Attempt attempt, int limit,
            ChannelSearchRequest request, List<SearchStep> steps, String correlationId) {
        if (markets.size() < 2) {
            List<SearchResult> single = new ArrayList<>();
            for (Marketplace mp : markets) {
                single.add(runStep(mp, attempt, limit, request, steps, correlationId));
            }
            return single;
        }
        // The adapters touch no tenant data today, but a worker thread starts
        // with an empty thread-local; carry the caller's scope across so a
        // future adapter that does cannot silently run unscoped.
        Long tenantId = com.priceintel.backend.security.TenantContext.getTenantId();
        boolean superAdmin = com.priceintel.backend.security.TenantContext.isSuperAdmin();

        List<java.util.concurrent.Future<ChannelOutcome>> pending = new ArrayList<>();
        try (var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            for (Marketplace mp : markets) {
                pending.add(pool.submit(() -> {
                    com.priceintel.backend.security.TenantContext.set(tenantId, superAdmin);
                    try {
                        List<SearchStep> own = new ArrayList<>();
                        return new ChannelOutcome(
                                runStep(mp, attempt, limit, request, own, correlationId), own);
                    } finally {
                        com.priceintel.backend.security.TenantContext.clear();
                    }
                }));
            }
        }
        List<SearchResult> results = new ArrayList<>();
        for (int i = 0; i < pending.size(); i++) {
            try {
                ChannelOutcome outcome = pending.get(i).get();
                steps.addAll(outcome.steps());
                results.add(outcome.result());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                results.add(null);
            } catch (java.util.concurrent.ExecutionException e) {
                // runStep already turns every failure into a recorded step;
                // reaching here means something outside it broke. One channel
                // failing must not take the other channel's results with it.
                log.warn("[{}] {} search failed unexpectedly: {}",
                        correlationId, markets.get(i), e.getCause() == null ? e : e.getCause().toString());
                results.add(null);
            }
        }
        return results;
    }

    // ---------- one channel call ----------

    private SearchResult runStep(Marketplace mp, Attempt attempt, int limit,
            ChannelSearchRequest request, List<SearchStep> steps, String correlationId) {
        long start = System.currentTimeMillis();
        SearchStep.SearchStepBuilder step = SearchStep.builder()
                .marketplace(mp.name())
                .query(attempt.value())
                .identifierType(attempt.type());
        try {
            // Null-checked first: a title attempt carries no type, and Set.of()
            // throws on contains(null) rather than answering false.
            SearchResult result = isDirectLookup(attempt.type())
                    ? fetchOne(mp, attempt.value(), request.getRegion())
                    : marketplaceService.search(mp, MarketplaceSearchRequest.builder()
                            .query(attempt.value())
                            .identifierType(attempt.type())
                            .maxResults(limit)
                            .requirePrice(request.isPriceRequired())
                            .destination(request.getDestination())
                            .region(request.getRegion())
                            .build());

            int count = result.getItems() == null ? 0 : result.getItems().size();
            steps.add(step
                    .outcome(count > 0 ? "SUCCESS" : "NO_RESULT")
                    .resultCount(count)
                    .sourceTimestamp(result.getSourceTimestamp() == null ? null
                            : result.getSourceTimestamp().toInstant(java.time.ZoneOffset.UTC))
                    .durationMs(System.currentTimeMillis() - start)
                    .detail(result.getNote())
                    .build());
            return count > 0 ? result : null;

        } catch (MarketplaceApiException e) {
            // Classified rather than lumped together: "we were refused" and
            // "there is nothing there" call for different actions, and a single
            // failure string cannot be counted or alerted on.
            steps.add(step
                    .outcome(classify(e))
                    .resultCount(0)
                    .durationMs(System.currentTimeMillis() - start)
                    .detail(e.getMessage())
                    .build());
            log.warn("[{}] {} search failed for '{}': {}",
                    correlationId, mp, attempt.value(), e.getMessage());
            return null;
        } catch (RuntimeException e) {
            steps.add(step.outcome("PROVIDER_ERROR").resultCount(0)
                    .durationMs(System.currentTimeMillis() - start)
                    .detail(e.getMessage()).build());
            log.warn("[{}] {} search errored for '{}': {}",
                    correlationId, mp, attempt.value(), e.getMessage());
            return null;
        }
    }

    /** FR-SRCH-002: each failure mode recorded as its own outcome. */
    private String classify(MarketplaceApiException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (msg.contains("403")) {
            return "RESTRICTED_ACCESS";
        }
        if (msg.contains("429")) {
            return "RATE_LIMITED";
        }
        if (msg.contains("401")) {
            return "AUTHENTICATION_ERROR";
        }
        if (msg.contains("404")) {
            return "NO_RESULT";
        }
        if (msg.contains("not configured")) {
            return "SKIPPED";
        }
        return "PROVIDER_ERROR";
    }

    // ---------- inputs ----------

    /**
     * Collects identifiers from every source the caller gave, in order of
     * reliability: explicit values, then a pasted URL, then an image matched
     * against the catalogue.
     */
    private Map<String, String> resolveIdentifiers(ChannelSearchRequest request,
            List<SearchStep> steps) {
        Map<String, String> resolved = new LinkedHashMap<>();
        if (request.getIdentifiers() != null) {
            request.getIdentifiers().forEach((k, v) -> {
                if (k != null && v != null && !v.isBlank()) {
                    resolved.put(k.trim().toUpperCase(), v.trim());
                }
            });
        }

        if (request.getUrl() != null && !request.getUrl().isBlank()) {
            long start = System.currentTimeMillis();
            var parsed = MarketplaceUrlParser.parse(request.getUrl());
            steps.add(SearchStep.builder()
                    .marketplace(parsed.map(p -> p.marketplace().name()).orElse("—"))
                    .query(request.getUrl())
                    .identifierType("URL")
                    .outcome(parsed.isPresent() ? "SUCCESS" : "NO_RESULT")
                    .resultCount(parsed.isPresent() ? 1 : 0)
                    .durationMs(System.currentTimeMillis() - start)
                    .detail(parsed.map(p -> p.identifierType() + " " + p.itemId()
                                    + (p.countryCode() != null ? " (" + p.countryCode() + ")" : ""))
                            .orElse("No product identifier in that URL — a search or category "
                                    + "page has no single item to extract."))
                    .build());
            // An explicit identifier wins: the caller stated it, the URL was inferred.
            parsed.ifPresent(p -> resolved.putIfAbsent(p.identifierType(), p.itemId()));
        }

        if (resolved.isEmpty() && request.getImage() != null && !request.getImage().isBlank()) {
            // Stored under a reserved key rather than returned separately: it is
            // a query the image produced, and the attempt list is built from
            // this map. Stripped out before anything treats it as an identifier.
            String words = resolveFromImage(request.getImage(), resolved, steps);
            if (words != null && !words.isBlank()) {
                resolved.put(IMAGE_QUERY_KEY, words);
            }
        }
        return resolved;
    }

    /**
     * Matches an image against our own catalogue and borrows the identifiers of
     * whatever it finds.
     *
     * <p>No marketplace accepts an image, so this cannot search outward. It
     * turns a photo into a product we already know, whose ASIN then does the
     * real work — and only when nothing better was supplied.</p>
     */
    private String resolveFromImage(String image, Map<String, String> resolved,
            List<SearchStep> steps) {
        long start = System.currentTimeMillis();
        try {
            var matches = imageSearchService.searchByImage(image);
            steps.add(SearchStep.builder()
                    .marketplace("CATALOG")
                    .identifierType("IMAGE")
                    .outcome(matches.isEmpty() ? "NO_RESULT" : "SUCCESS")
                    .resultCount(matches.size())
                    .durationMs(System.currentTimeMillis() - start)
                    .detail(matches.isEmpty()
                            ? "No catalogue product matched that image"
                            : "Matched " + matches.get(0).getTitle()
                                    + " (" + matches.get(0).getSimilarityPct() + "% similar)")
                    .build());
            if (!matches.isEmpty()) {
                return matches.get(0).getTitle();
            }
        } catch (RuntimeException e) {
            steps.add(SearchStep.builder()
                    .marketplace("CATALOG").identifierType("IMAGE")
                    .outcome("PROVIDER_ERROR").resultCount(0)
                    .durationMs(System.currentTimeMillis() - start)
                    .detail(e.getMessage()).build());
        }
        return describeImage(image, steps);
    }

    /**
     * Asks a model what the photograph shows, when our own catalogue does not
     * recognise it.
     *
     * <p>Second, not first: a catalogue match is an exact hit on a product we
     * already track, while a description is a guess that may fit thousands of
     * listings. The words go to the marketplace's keyword search — the model
     * supplies no price and no identifier, only what to search for.</p>
     */
    private String describeImage(String image, List<SearchStep> steps) {
        if (!imageDescriptionService.isAvailable()) {
            return null;
        }
        long start = System.currentTimeMillis();
        String words = imageDescriptionService.describe(image);
        steps.add(SearchStep.builder()
                .marketplace("AI")
                .identifierType("IMAGE")
                .outcome(words.isBlank() ? "NO_RESULT" : "SUCCESS")
                .resultCount(words.isBlank() ? 0 : 1)
                .durationMs(System.currentTimeMillis() - start)
                .detail(words.isBlank()
                        ? "The model could not describe that image"
                        : "Described as \"" + words + "\"")
                .build());
        return words.isBlank() ? null : words;
    }

    /**
     * Which marketplaces to call, honouring an explicit list and then a
     * preferred region.
     */
    private List<Marketplace> resolveMarkets(ChannelSearchRequest request) {
        if (request.getMarkets() != null && !request.getMarkets().isEmpty()) {
            List<Marketplace> chosen = new ArrayList<>();
            for (String m : request.getMarkets()) {
                try {
                    chosen.add(Marketplace.valueOf(m.trim().toUpperCase()));
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException("Unknown marketplace: " + m);
                }
            }
            return chosen;
        }
        return List.of(Marketplace.AMAZON, Marketplace.EBAY);
    }

    // ---------- narration ----------

    private String describe(Attempt attempt) {
        return attempt.type() == null
                ? "title \"" + attempt.value() + "\""
                : attempt.type() + " " + attempt.value();
    }

    private String noteFor(Attempt attempt, SearchResult result, List<SearchStep> steps) {
        if (attempt.type() == null) {
            long identifierAttempts = steps.stream()
                    .filter(s -> s.getIdentifierType() != null
                            && !"URL".equals(s.getIdentifierType())
                            && !"IMAGE".equals(s.getIdentifierType()))
                    .count();
            if (identifierAttempts > 0) {
                return "No identifier matched, so these were found by title — check they are the "
                        + "same product before pricing against them.";
            }
            return "Found by title. Supply an identifier for an exact match.";
        }
        return result.getNote();
    }

    /** Turns an empty result into the reason for it. */
    private String emptyNote(List<SearchStep> steps) {
        if (steps.isEmpty()) {
            return "Nothing was searched — no usable identifier, title or URL.";
        }
        if (steps.stream().anyMatch(s -> "RESTRICTED_ACCESS".equals(s.getOutcome()))) {
            return "One or more marketplaces refused the request. The connector is not authorised "
                    + "for them, so this is not evidence the product is absent.";
        }
        if (steps.stream().anyMatch(s -> "RATE_LIMITED".equals(s.getOutcome()))) {
            return "The marketplace rate-limited us. Try again shortly.";
        }
        if (steps.stream().anyMatch(s -> "AUTHENTICATION_ERROR".equals(s.getOutcome()))) {
            return "The marketplace rejected our credentials. Check the connector settings.";
        }
        return "Searched every channel and found nothing. Check the identifier is correct and "
                + "that the product is sold in the regions we can reach.";
    }

    /** One thing to search for, and what kind of thing it is. */
    private record Attempt(String value, String type) {
    }
}
