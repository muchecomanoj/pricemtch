package com.priceintel.backend.controller;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.priceintel.backend.constants.MatchStatus;
import com.priceintel.backend.dto.request.ReviewRequest;
import com.priceintel.backend.dto.request.SearchJobRequest;
import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.dto.response.CompetitorListingResponse;
import com.priceintel.backend.dto.response.MarketPricesResponse;
import com.priceintel.backend.dto.response.SearchJobResponse;
import com.priceintel.backend.entity.ListingPriceSnapshot;
import com.priceintel.backend.service.impl.SearchPipelineService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Product search orchestration + competitor listings, market prices, price
 * history, and match review (FR-SRCH / FR-PRICE / FR-MATCH).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Product Search & Competitors",
        description = "Search marketplaces for a product, review matches, and read competitor prices")
public class SearchPipelineController {

    private static final String WRITE_ROLES = "hasAnyRole('ADMIN', 'MANAGER', 'SUPER_ADMIN')";

    private final SearchPipelineService service;
    private final com.priceintel.backend.service.impl.AiMatchAdvisorService aiAdvisor;
    private final com.priceintel.backend.service.impl.IdempotencyService idempotency;
    private final com.priceintel.backend.service.impl.ChartExportService chartExportService;

    @PostMapping("/products/{id}/search-jobs")
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Search marketplaces for this product; stores candidate competitor listings + prices",
            description = "Reuses a recent fetch of the same query when one exists (shared across products "
                    + "and clients). Pass forceRefresh=true, or ?refresh=true, to bypass the cache and "
                    + "re-fetch live.")
    public ResponseEntity<ApiResponse<SearchJobResponse>> startSearch(
            @PathVariable Long id,
            @RequestBody(required = false) SearchJobRequest request,
            @RequestParam(name = "refresh", defaultValue = "false") boolean refresh,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        SearchJobRequest req = request != null ? request : new SearchJobRequest();
        boolean force = refresh || req.isForceRefresh();
        // Each search spends marketplace quota shared across every client, so a
        // double-click costs everyone. Repeats collapse into one call.
        SearchJobResponse result = idempotency.execute(
                "SEARCH_JOB", idempotencyKey,
                // The destination belongs in the key: the same search delivered
                // to two places is two different answers, and collapsing them
                // would serve one buyer's postage as the other's.
                id + "|" + req.getMarkets() + "|" + req.getMaxResults() + "|" + force
                        + "|" + destinationKey(req.getDestination()),
                SearchJobResponse.class,
                () -> service.runSearch(id, req.getMarkets(), req.getMaxResults(), force,
                        null, req.getDestination()));
        return ResponseEntity.ok(ApiResponse.success(result,
                force ? "Search complete (refreshed from marketplace)" : "Search complete"));
    }

    /** Stable text for a destination, so it can take part in an idempotency key. */
    private String destinationKey(com.priceintel.backend.dto.request.Destination d) {
        return d == null || d.isEmpty() ? "-"
                : d.normalisedCountry() + ":" + d.normalisedPostalCode();
    }

    @GetMapping("/search-jobs/{jobId}")
    @Operation(summary = "Get a search job's status and result count")
    public ResponseEntity<ApiResponse<SearchJobResponse>> job(@PathVariable Long jobId) {
        return ResponseEntity.ok(ApiResponse.success(service.getJob(jobId), "Search job"));
    }

    @GetMapping("/competitor-listings")
    @Operation(summary = "Tenant-wide competitor listings (paginated + filterable) for the Competitor Listings page")
    public ResponseEntity<ApiResponse<Page<CompetitorListingResponse>>> listings(
            @RequestParam(required = false) String marketplace,
            @RequestParam(required = false) String condition,
            @RequestParam(required = false) MatchStatus matchStatus,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "sourceTimestamp"));
        return ResponseEntity.ok(ApiResponse.success(
                service.listAll(marketplace, condition, matchStatus, search, pageable),
                "Competitor listings"));
    }

    @GetMapping("/match-review")
    @Operation(summary = "Tenant-wide candidates awaiting review, scored with confidence + signals")
    public ResponseEntity<ApiResponse<Page<CompetitorListingResponse>>> matchReview(
            @RequestParam(required = false) Long productId,
            @RequestParam(defaultValue = "score") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // sort: "score" = highest confidence first (default); "lowest" = weakest
        //       matches first (the risky ones a reviewer should scrutinize);
        //       "recent" = newest first.
        Sort order = switch (sort) {
            case "recent" -> Sort.by(Sort.Direction.DESC, "sourceTimestamp");
            // nullsLast so legacy rows without a stored score sink to the bottom.
            case "lowest" -> Sort.by(new Sort.Order(Sort.Direction.ASC, "matchScore").nullsLast());
            default -> Sort.by(new Sort.Order(Sort.Direction.DESC, "matchScore").nullsLast());
        };
        Pageable pageable = PageRequest.of(page, size,
                order.and(Sort.by(Sort.Direction.DESC, "sourceTimestamp")));
        return ResponseEntity.ok(ApiResponse.success(
                service.pendingReview(productId, pageable), "Match review queue"));
    }

    @GetMapping("/products/{id}/candidates")
    @Operation(summary = "List competitor listings (candidates) for a product")
    public ResponseEntity<ApiResponse<List<CompetitorListingResponse>>> candidates(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.getCandidates(id), "Candidates"));
    }

    @PostMapping("/products/{id}/ai-review")
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Ask the model to judge this product's unjudged candidates",
            description = "Advisory only — no candidate is accepted or rejected automatically. "
                    + "Verdicts are cached, so a second call costs nothing for pairs already "
                    + "judged. `limit` caps the calls spent in one pass; the default suits a free "
                    + "tier's per-minute token budget. Returns 409 when no AI provider is "
                    + "configured.")
    public ResponseEntity<ApiResponse<List<CompetitorListingResponse>>> aiReview(
            @PathVariable Long id,
            @RequestParam(defaultValue = "20") int limit) {
        if (!aiAdvisor.isAvailable()) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
                    .body(ApiResponse.error("No AI provider is configured. Set openai.enabled "
                            + "and an API key to use AI review."));
        }
        // The AI budget is metered by the minute, so a repeated click must not
        // spend it twice. The verdict cache already prevents re-judging a pair;
        // this stops the second request doing the work at all.
        int capped = Math.min(Math.max(limit, 1), 50);
        idempotency.execute("AI_REVIEW", null, id + "|" + capped, Integer.class,
                () -> aiAdvisor.adviseForProduct(id, capped).size());
        // Re-read so every row carries its verdict, cached ones included.
        return ResponseEntity.ok(ApiResponse.success(
                service.getCandidates(id), "AI review complete"));
    }

    @PostMapping("/products/{id}/competitor-listings")
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Attach a marketplace listing to this product as a competitor",
            description = "For a listing found by hand rather than by search. The price, title "
                    + "and seller are fetched live from the marketplace — only the item id is "
                    + "taken from the request, so nothing stored is hand-typed. Saved as a "
                    + "CANDIDATE for review, never as a confirmed match.\n\n"
                    + "**`region`** is the storefront the listing was found in — send the "
                    + "`storefront` from the search result (`US`, `CA`, `GB`). The listing is "
                    + "then fetched from exactly that storefront. Without it, the listing is "
                    + "fetched from whichever storefront answers first with a price, so a "
                    + "listing seen on amazon.ca at CA$140 was stored as amazon.com's US$144.29.\n\n"
                    + "The same item id can be attached once **per storefront**, as separate "
                    + "competitors with separate price histories. Attaching the same listing in "
                    + "the same storefront twice returns the existing one.\n\n"
                    + "A storefront the connector is not authorized for returns an error rather "
                    + "than a different country's listing.")
    public ResponseEntity<ApiResponse<CompetitorListingResponse>> attachListing(
            @PathVariable Long id,
            @RequestParam com.priceintel.backend.marketplace.Marketplace marketplace,
            @RequestParam String marketplaceItemId,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) MatchStatus decision) {
        CompetitorListingResponse attached =
                service.attachListing(id, marketplace, marketplaceItemId, region);
        if (decision == null) {
            return ResponseEntity.ok(ApiResponse.success(attached,
                    "Listing attached as a candidate"));
        }
        // Attaching and deciding in one call, because a search result is not
        // stored: accepting one from the results otherwise meant attaching it
        // and then reviewing it, two requests for what the user experienced as
        // a single click. The pack and condition rules still apply to the
        // decision exactly as they do on the review screen.
        return ResponseEntity.ok(ApiResponse.success(
                service.review(attached.getId(), decision),
                "Listing attached and marked " + decision));
    }

    @PostMapping("/candidates/{listingId}/review")
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Accept/reject/mark a candidate (MATCHED, EQUIVALENT, REJECTED)")
    public ResponseEntity<ApiResponse<CompetitorListingResponse>> review(
            @PathVariable Long listingId, @Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                service.review(listingId, request.getDecision()), "Reviewed"));
    }

    @GetMapping("/products/{id}/market-prices")
    @Operation(summary = "Competitor price statistics (lowest/median/average/highest)")
    public ResponseEntity<ApiResponse<MarketPricesResponse>> marketPrices(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean matchedOnly) {
        return ResponseEntity.ok(ApiResponse.success(
                service.marketPrices(id, matchedOnly), "Market prices"));
    }

    @GetMapping("/products/{id}/price-history-rollup")
    @Operation(summary = "Product-level rolled-up price history (competitor low/high/median/avg per day or week) for the chart")
    public ResponseEntity<ApiResponse<List<com.priceintel.backend.dto.response.PriceHistoryPoint>>> priceHistoryRollup(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "day") String bucket) {
        return ResponseEntity.ok(ApiResponse.success(
                service.priceHistoryRollup(id, from, to, bucket), "Price history rollup"));
    }

    @GetMapping("/listings/{listingId}/price-history/export")
    @Operation(summary = "One listing's price history as a CSV file",
            description = "Every price recorded for this listing in the range, with item price, "
                    + "shipping and landed price — the figures behind the 'Price over time' chart.")
    public ResponseEntity<byte[]> exportPriceHistory(
            @PathVariable Long listingId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        byte[] csv = chartExportService.listingPriceHistory(listingId, from, to);
        return ResponseEntity.ok()
                .headers(com.priceintel.backend.utils.CsvExport.headers(
                        com.priceintel.backend.utils.CsvExport.filename("listing-" + listingId + "-price-history")))
                .body(csv);
    }

    @GetMapping("/products/{id}/price-history-rollup/export")
    @Operation(summary = "A product's market prices over time as a CSV file",
            description = "Lowest, median, average and highest competitor price per period, with our "
                    + "own price and how many competitors were seen.")
    public ResponseEntity<byte[]> exportPriceHistoryRollup(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "day") String bucket) {
        byte[] csv = chartExportService.productPriceHistory(id, from, to, bucket);
        return ResponseEntity.ok()
                .headers(com.priceintel.backend.utils.CsvExport.headers(
                        com.priceintel.backend.utils.CsvExport.filename("product-" + id + "-price-history")))
                .body(csv);
    }

    @GetMapping("/listings/{listingId}/price-history")
    @Operation(summary = "Price history for a competitor listing (optional from/to range)")
    public ResponseEntity<ApiResponse<List<ListingPriceSnapshot>>> priceHistory(
            @PathVariable Long listingId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(ApiResponse.success(
                service.priceHistory(listingId, from, to), "Price history"));
    }
}
