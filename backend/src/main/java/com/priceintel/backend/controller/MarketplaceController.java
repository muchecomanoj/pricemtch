package com.priceintel.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.priceintel.backend.dto.request.FeeEstimateRequest;
import com.priceintel.backend.dto.request.MarketplaceSearchRequest;
import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.marketplace.Marketplace;
import com.priceintel.backend.service.MarketplaceService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Live marketplace connectors. The {marketplace} path variable (AMAZON / EBAY)
 * selects the adapter via the factory, and is matched case-insensitively.
 *
 * <p>Every response here comes from the real provider API. Where a figure cannot
 * be obtained — competitor unit sales, which Amazon does not publish — the
 * response says {@code UNAVAILABLE} rather than inventing one.</p>
 */
@RestController
@RequestMapping("/api/v1/marketplaces")
@RequiredArgsConstructor
@Tag(name = "Marketplace Connectors",
        description = "Live Amazon and eBay connectors — search, listings, prices, offers and fees")
public class MarketplaceController {

    private final MarketplaceService marketplaceService;
    private final com.priceintel.backend.service.impl.ChannelSearchPlanner planner;
    private final com.priceintel.backend.service.impl.AsinSuggestionService asinSuggestions;
    private final com.priceintel.backend.service.impl.EbayOtherSellersService otherSellers;

    @GetMapping
    @Operation(summary = "List supported marketplaces")
    public ResponseEntity<ApiResponse<Object>> supported() {
        return ResponseEntity.ok(ApiResponse.success(
                marketplaceService.getSupportedMarketplaces(), "Supported marketplaces"));
    }

    @GetMapping("/health")
    @Operation(summary = "Health of all marketplace connectors")
    public ResponseEntity<ApiResponse<Object>> healthAll() {
        return ResponseEntity.ok(ApiResponse.success(marketplaceService.healthAll(), "Connector health"));
    }

    @GetMapping("/{marketplace}/health")
    @Operation(summary = "Health of one marketplace connector")
    public ResponseEntity<ApiResponse<Object>> health(@PathVariable Marketplace marketplace) {
        return ResponseEntity.ok(ApiResponse.success(marketplaceService.health(marketplace), "Connector health"));
    }

    @PostMapping("/search")
    @Operation(summary = "Search the channels and, optionally, judge the results (FR-SRCH-001/002/003)",
            description = "One pipeline: resolve what to search for (identifiers, then title, then "
                    + "an image), search the channels, and — with `judge: true` — decide which "
                    + "results are the same product. Every step is reported with a distinct "
                    + "outcome, under one correlation ID.\n\n"
                    + "Judging is worth requesting even for an exact identifier: an ASIN says how "
                    + "a candidate was found, not that it is acceptable — the same identifier "
                    + "family returns two-packs beside singles and renewed units beside new.\n\n"
                    + "`matches` and `rejected` are populated only when judging was asked for; "
                    + "`status` is NOT_JUDGED otherwise. Rejected listings are returned with their "
                    + "reasons, because \"none matched\" is less useful than \"none matched, and "
                    + "here is why\".")
    public ResponseEntity<ApiResponse<com.priceintel.backend.dto.response.ChannelSearchResponse>>
            planAndSearch(@Valid @RequestBody
                    com.priceintel.backend.dto.request.ChannelSearchRequest request) {
        return ResponseEntity.ok(ApiResponse.success(planner.search(request), "Search complete"));
    }

    @PostMapping("/find-asin")
    @Operation(summary = "Find Amazon ASINs from a barcode, part number or product name",
            description = "For when the marketplace's own identifier lookup returns nothing — a "
                    + "UPC Amazon does not index, an MPN it cannot resolve, or a product known "
                    + "only by name. A web-searching model proposes candidates and every one is "
                    + "verified against Amazon before it is returned, with Amazon's title and "
                    + "price rather than the model's. Nothing is saved. Returns 400 when no "
                    + "web-search model is configured.")
    public ResponseEntity<ApiResponse<com.priceintel.backend.dto.response.AsinLookupResponse>>
            findAsin(
            @RequestParam String query,
            @RequestParam(required = false) String identifierType) {
        return ResponseEntity.ok(ApiResponse.success(
                asinSuggestions.suggestFor(query, identifierType), "ASIN suggestions"));
    }

    @PostMapping("/parse-url")
    @Operation(summary = "Extract the item identifier from a pasted marketplace URL",
            description = "Amazon /dp/, /gp/product/, /gp/aw/d/ and ?asin= forms, plus eBay /itm/. "
                    + "Returns the marketplace, the identifier and the country the link belongs to. "
                    + "Empty when the URL is a search or category page.")
    public ResponseEntity<ApiResponse<com.priceintel.backend.utils.MarketplaceUrlParser.ParsedUrl>>
            parseUrl(@RequestParam String url) {
        return com.priceintel.backend.utils.MarketplaceUrlParser.parse(url)
                .map(parsed -> ResponseEntity.ok(ApiResponse.success(parsed, "Parsed")))
                .orElseGet(() -> ResponseEntity.ok(ApiResponse.success(null,
                        "No product identifier found in that URL")));
    }

    @PostMapping("/{marketplace}/search")
    @Operation(summary = "Search a marketplace live",
            description = "Results are in `items`. Set `identifierType` (ASIN/UPC/EAN/GTIN) to resolve "
                    + "the query as an identifier instead of keywords. `regionsTried` and "
                    + "`regionsUnavailable` explain an empty result set.")
    public ResponseEntity<ApiResponse<com.priceintel.backend.marketplace.model.SearchResult>> search(
            @PathVariable Marketplace marketplace,
            @Valid @RequestBody MarketplaceSearchRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                marketplaceService.search(marketplace, request), "Search results"));
    }

    @GetMapping("/{marketplace}/listings/{itemId}")
    @Operation(summary = "Get listing details",
            description = "`region` (`US`, `CA`, `GB`) fetches the listing from exactly that "
                    + "storefront — pass a listing's `storefront`. Omitted, the first storefront "
                    + "with a price answers, which may not be the one the listing belongs to.")
    public ResponseEntity<ApiResponse<com.priceintel.backend.marketplace.model.ListingDetails>> listing(
            @PathVariable Marketplace marketplace, @PathVariable String itemId,
            @RequestParam(required = false) String region) {
        return ResponseEntity.ok(ApiResponse.success(
                marketplaceService.getListing(marketplace, itemId, storefrontParam(region)),
                "Listing details"));
    }

    @GetMapping("/{marketplace}/listings/{itemId}/price")
    @Operation(summary = "Get the current price for a listing",
            description = "`region` pins the storefront, as on listing details.")
    public ResponseEntity<ApiResponse<Object>> price(
            @PathVariable Marketplace marketplace, @PathVariable String itemId,
            @RequestParam(required = false) String region) {
        return ResponseEntity.ok(ApiResponse.success(
                marketplaceService.getPrice(marketplace, itemId, storefrontParam(region)),
                "Price snapshot"));
    }

    /** A validated storefront code, or null when none was given. */
    private String storefrontParam(String region) {
        if (region == null || region.isBlank()) {
            return null;
        }
        String sf = com.priceintel.backend.utils.Storefront.normalise(region);
        if (sf == null) {
            throw new com.priceintel.backend.exception.BadRequestException("Unknown region: "
                    + region + ". Use a two-letter country code such as US, CA or GB.");
        }
        return sf;
    }

    @GetMapping("/{marketplace}/listings/{itemId}/offers")
    @Operation(summary = "Competing sellers' offers for a listing (price, shipping, fulfilment, feedback)",
            description = "Returns **New and Used** offers by default, each carrying its own "
                    + "`condition`, so the caller can filter rather than being filtered for. Pass "
                    + "`condition` (New, Used, Collectible, Refurbished) to narrow it — Amazon's "
                    + "endpoint returns one condition per call, so the default costs two calls.\n\n"
                    + "At most one offer per condition is flagged `buyBoxWinner`. Amazon sometimes "
                    + "reports the same seller twice, and occasionally flags two winners of one "
                    + "condition; duplicates are removed and the cheaper offer keeps the flag.\n\n"
                    + "**No delivery destination.** Amazon's pricing API takes a marketplace, not a "
                    + "postcode, so `shipping` is whatever the seller states for that marketplace "
                    + "and cannot be quoted for an address. The `destination` accepted on search "
                    + "reaches eBay, which does support it; there is nothing to pass it to here.\n\n"
                    + "**`region`** (`US`, `CA`, `GB`) returns that storefront's sellers only — pass "
                    + "the listing's `storefront`. The Buy Box on amazon.com says nothing about who "
                    + "holds it on amazon.ca. Omitted, the first storefront with offers answers.")
    public ResponseEntity<ApiResponse<Object>> offers(
            @PathVariable Marketplace marketplace,
            @PathVariable String itemId,
            @RequestParam(required = false) String condition,
            @RequestParam(required = false) String region) {
        return ResponseEntity.ok(ApiResponse.success(
                marketplaceService.getOffers(marketplace, itemId, condition, storefrontParam(region)),
                "Seller offers"));
    }


    @GetMapping("/{marketplace}/listings/{itemId}/other-sellers")
    @Operation(summary = "Other eBay sellers of the same product (eBay only)",
            description = "eBay's answer to Amazon's offers panel. eBay has no Buy Box and no shared "
                    + "product page — every seller has a separate listing — so a listing's "
                    + "competitors are the **other listings of the same product**.\n\n"
                    + "They are found by the listing's UPC/EAN (`matchedBy: GTIN`), or eBay's "
                    + "product id (`EPID`) when it has no barcode. A listing with neither returns "
                    + "only itself and a `note` explaining why; a title search would return "
                    + "similar products, not this one.\n\n"
                    + "**One row per seller**, cheapest landed price first. `listingsFromSeller` "
                    + "says when a seller has several. Each row names the seller and their "
                    + "`feedbackPercent` and `feedbackScore` — a 99.8% seller and a 60% one are not "
                    + "equal competitors.\n\n"
                    + "**Cheapest is per condition** (`cheapestInCondition`): a used unit at $40 "
                    + "does not undercut a new one at $60. `cheapestLandedPrice` is the cheapest in "
                    + "the source listing's own condition.\n\n"
                    + "**Auctions are excluded** — an auction's price is its current bid, not a "
                    + "price anyone can buy at.\n\n"
                    + "**Shipping.** `landedPrice` is null when a seller quoted no shipping, and such "
                    + "rows are never marked cheapest. Pass `deliveryCountry` (and optionally "
                    + "`deliveryPostalCode`) so sellers quote delivery; without it many will not.\n\n"
                    + "`region` (`US`, `GB`, `CA`…) is the eBay site to compare on — pass the "
                    + "listing's `storefront`.")
    public ResponseEntity<ApiResponse<com.priceintel.backend.dto.response.EbayOtherSellersResponse>>
            otherSellers(
            @PathVariable Marketplace marketplace,
            @PathVariable String itemId,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String deliveryCountry,
            @RequestParam(required = false) String deliveryPostalCode,
            @RequestParam(required = false) Integer limit) {
        if (marketplace != Marketplace.EBAY) {
            throw new com.priceintel.backend.exception.BadRequestException(
                    "Other sellers applies to eBay only. On Amazon, competing sellers are offers on "
                            + "one listing — use /" + marketplace.name().toLowerCase()
                            + "/listings/" + itemId + "/offers.");
        }
        if (deliveryCountry != null && !deliveryCountry.isBlank()
                && !deliveryCountry.trim().matches("[A-Za-z]{2}")) {
            throw new com.priceintel.backend.exception.BadRequestException(
                    "deliveryCountry must be a two-letter country code such as US or GB.");
        }
        var destination = com.priceintel.backend.dto.request.Destination.builder()
                .country(deliveryCountry).postalCode(deliveryPostalCode).build();
        return ResponseEntity.ok(ApiResponse.success(
                otherSellers.find(itemId, region, destination, limit), "Other sellers"));
    }

    @GetMapping("/{marketplace}/listings/{itemId}/sales")
    @Operation(summary = "Get sales figures — owned actual, competitor estimated")
    public ResponseEntity<ApiResponse<Object>> sales(
            @PathVariable Marketplace marketplace, @PathVariable String itemId) {
        return ResponseEntity.ok(ApiResponse.success(
                marketplaceService.getSales(marketplace, itemId), "Sales estimate"));
    }

    @PostMapping("/{marketplace}/fees")
    @Operation(summary = "Estimate marketplace fees at a given price")
    public ResponseEntity<ApiResponse<Object>> fees(
            @PathVariable Marketplace marketplace,
            @Valid @RequestBody FeeEstimateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                marketplaceService.estimateFees(marketplace, request), "Fee estimate"));
    }
}
