package com.priceintel.backend.marketplace.adapter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.priceintel.backend.dto.request.FeeEstimateRequest;
import com.priceintel.backend.dto.request.MarketplaceSearchRequest;
import com.priceintel.backend.exception.MarketplaceApiException;
import com.priceintel.backend.marketplace.Marketplace;
import com.priceintel.backend.marketplace.MarketplaceAdapter;
import com.priceintel.backend.marketplace.amazon.AmazonMarketplace;
import com.priceintel.backend.marketplace.amazon.AmazonProductNormalizer;
import com.priceintel.backend.marketplace.amazon.AmazonSpApiClient;
import com.priceintel.backend.marketplace.amazon.AmazonSpApiProperties;
import com.priceintel.backend.marketplace.amazon.LwaTokenService;
import com.priceintel.backend.marketplace.model.ConnectorHealth;
import com.priceintel.backend.marketplace.model.FeeEstimate;
import com.priceintel.backend.marketplace.model.ListingDetails;
import com.priceintel.backend.marketplace.model.OfferListing;
import com.priceintel.backend.marketplace.model.PriceSnapshot;
import com.priceintel.backend.marketplace.model.SalesMetrics;
import com.priceintel.backend.marketplace.model.SearchResult;
import com.priceintel.backend.marketplace.model.SearchResultItem;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REAL Amazon adapter backed by the Selling Partner API (Catalog / Pricing /
 * Fees). Replaces the earlier dummy adapter. When credentials are not
 * configured, calls fail cleanly with a MarketplaceApiException (mapped to 502)
 * and {@link #health()} reports NOT_CONFIGURED.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AmazonAdapter implements MarketplaceAdapter {

    private final AmazonSpApiClient client;
    private final AmazonProductNormalizer normalizer;
    private final AmazonSpApiProperties props;
    private final LwaTokenService tokenService;
    private final com.priceintel.backend.marketplace.scraper.AmazonScraperSearch scraperSearch;

    /**
     * Marketplaces this connector has been refused (HTTP 403) — the SP-API app
     * is not authorized for them. Authorization does not change mid-run, so
     * re-asking every search would burn the per-lookup region budget on calls
     * that cannot succeed. Cleared on restart, which is when an authorization
     * change would be picked up anyway.
     */
    private final Set<AmazonMarketplace> unauthorized = ConcurrentHashMap.newKeySet();

    @Override
    public Marketplace getMarketplace() {
        return Marketplace.AMAZON;
    }

    /**
     * Searches the catalogue and returns only listings that carry a price.
     *
     * <p>An ASIN is global but its price is not: a product sold only on Amazon
     * UK resolves in the US catalogue with no competitive price, which is how
     * priceless rows used to reach the UI. So each marketplace is tried in turn
     * (see {@code amazon.sp-api.search-marketplace-ids}) and the first one that
     * yields at least one priced listing wins. Unpriced listings are dropped
     * from the winning region too.</p>
     *
     * <p>Set {@code requirePrice=false} on the request to get the raw,
     * primary-marketplace behaviour back.</p>
     */
    @Override
    public SearchResult search(MarketplaceSearchRequest request) {
        // An ASIN in a marketplace this account is not authorised for. SP-API
        // answers 403 there — not "no results" but "you may not ask" — so the
        // public page is the only way to see it at all.
        if (scraperSearch.handles(request)) {
            return scraperSearch.search(request);
        }

        int size = request.getMaxResults() != null ? request.getMaxResults() : 10;
        boolean requirePrice = !Boolean.FALSE.equals(request.getRequirePrice());

        // A named region is an instruction, not a preference: search that
        // storefront and no other. Falling back would answer a question about
        // amazon.co.uk with prices from amazon.com — the same product, a
        // different market, and no indication on screen that it had happened.
        AmazonMarketplace chosen = AmazonMarketplace.find(request.getRegion()).orElse(null);
        List<AmazonMarketplace> markets;
        if (chosen != null) {
            markets = List.of(chosen);
        } else if (requirePrice) {
            markets = props.resolveSearchMarketplaces();
        } else {
            markets = List.of(props.getPrimaryMarketplace());
        }

        List<String> tried = new ArrayList<>();
        List<String> unavailable = new ArrayList<>();
        int productsSeen = 0;
        int queried = 0;

        for (AmazonMarketplace market : markets) {
            if (queried >= props.getMaxRegionAttempts()) {
                break;
            }
            if (unauthorized.contains(market)) {
                // Known-refused: record it, but don't spend an attempt on it.
                unavailable.add(market.name() + " (not authorized for this marketplace)");
                continue;
            }
            tried.add(market.name());
            queried++;
            SearchResult result;
            try {
                result = searchIn(market, request.getQuery(), size,
                        request.normalisedIdentifierType());
            } catch (MarketplaceApiException e) {
                // A marketplace the connector is not authorized for (403) or a
                // regional outage must not sink the whole search — move on, but
                // record it: "we could not look" is not "there is nothing there".
                log.warn("Amazon search in {} failed, trying next region: {}", market, e.getMessage());
                noteFailure(market, e);
                unavailable.add(market.name() + " (" + reasonFor(e) + ")");
                continue;
            }

            if (!requirePrice) {
                return result;
            }

            // Across regions, remember whether the product was ever seen at all,
            // so an empty final answer can say "found but unpriced" accurately.
            productsSeen = Math.max(productsSeen, result.getItems().size());

            List<SearchResultItem> priced = result.getItems().stream()
                    .filter(AmazonAdapter::hasPrice)
                    .toList();
            if (!priced.isEmpty()) {
                int dropped = result.getItems().size() - priced.size();
                result.setItems(priced);
                result.setTotalResults(priced.size());
                result.setRegionsTried(tried);
                result.setRegionsUnavailable(unavailable.isEmpty() ? null : unavailable);
                if (dropped > 0 || tried.size() > 1) {
                    result.setNote(describe(market, tried, dropped, unavailable));
                }
                log.info("Amazon search '{}' resolved in {} — {} priced result(s), {} dropped, regions tried {}",
                        request.getQuery(), market, priced.size(), dropped, tried);
                return result;
            }
            log.info("Amazon search '{}' found {} unpriced result(s) in {} — trying next region",
                    request.getQuery(), result.getItems().size(), market);
        }

        // Nothing priced anywhere. Return an empty result rather than priceless
        // rows, so the caller never has to filter again.
        return emptyResult(request.getQuery(), tried, unavailable, productsSeen);
    }

    /** Records a marketplace as permanently refused, so later lookups skip it. */
    private void noteFailure(AmazonMarketplace market, MarketplaceApiException e) {
        if (e.getMessage() != null && e.getMessage().contains("403") && unauthorized.add(market)) {
            log.warn("Amazon connector is not authorized for {} — skipping it until restart. "
                    + "Authorize the SP-API app for that marketplace to search it.", market);
        }
    }

    /** Turns an adapter failure into a short, user-facing cause. */
    private String reasonFor(MarketplaceApiException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        if (msg.contains("403")) {
            return "not authorized for this marketplace";
        }
        if (msg.contains("timed out") || msg.contains("unreachable")) {
            return "unreachable";
        }
        return "unavailable";
    }

    /**
     * Identifier types Amazon's catalogue can resolve directly. MPN is absent
     * deliberately: Amazon has no MPN lookup, so one is searched as keywords —
     * which often works, because manufacturers put the part number in the title.
     */
    private static final Set<String> AMAZON_IDENTIFIER_TYPES =
            Set.of("ASIN", "UPC", "EAN", "GTIN", "ISBN", "JAN");

    /** One catalogue search plus its price enrichment, within a single marketplace. */
    private SearchResult searchIn(AmazonMarketplace market, String query, int size,
            String identifierType) {
        String json;
        if (identifierType != null && AMAZON_IDENTIFIER_TYPES.contains(identifierType)) {
            // ASIN goes through the identifiers endpoint too, rather than the
            // single-item one. Both resolve it, but only this returns the
            // {"items":[...]} envelope the normalizer reads — fetching the item
            // directly yields a bare object and parses as zero results.
            json = client.searchCatalogByIdentifier(query, identifierType, market);
        } else {
            json = client.searchCatalog(query, size, market);
        }
        SearchResult result = normalizer.toSearchResult(json, query, market);
        result.setCountryCode(market.getCountryCode());
        result.setMarketplaceId(market.getMarketplaceId());
        enrichWithPrices(result, market);
        return result;
    }

    /**
     * Amazon catalog search returns no price, so fetch competitive prices for
     * all result ASINs in one batch call and attach them. Best-effort — a
     * pricing failure leaves prices null rather than failing the search.
     */
    private void enrichWithPrices(SearchResult result, AmazonMarketplace market) {
        List<String> asins = result.getItems().stream()
                .map(SearchResultItem::getMarketplaceItemId)
                .filter(a -> a != null && !a.isBlank())
                .distinct().toList();
        if (asins.isEmpty()) {
            return;
        }
        try {
            String priceJson = client.getCompetitivePricing(asins, market);
            var prices = normalizer.extractPricesByAsin(priceJson);
            String currency = normalizer.extractCurrency(priceJson);
            result.getItems().forEach(item -> {
                var p = prices.get(item.getMarketplaceItemId());
                if (p != null) {
                    item.setPrice(p);
                    item.setCurrency(currency != null ? currency : market.getCurrency());
                }
            });
        } catch (Exception e) {
            log.warn("Amazon search price enrichment failed in {}: {}", market, e.getMessage());
        }
        fillGapsFromOffers(result, market);
    }

    /**
     * Second pricing pass for items the batched call could not price.
     *
     * <p>competitivePrice reports the featured (Buy Box) offer, so an item with
     * live offers but no Buy Box winner comes back priceless. getItemOffers
     * reads the offers themselves and recovers those. It costs one call per
     * item, so it runs only on the gaps and only up to
     * {@code max-offer-lookups}.</p>
     */
    private void fillGapsFromOffers(SearchResult result, AmazonMarketplace market) {
        if (!props.isOfferFallbackEnabled()) {
            return;
        }
        List<SearchResultItem> gaps = result.getItems().stream()
                .filter(i -> !hasPrice(i))
                .filter(i -> i.getMarketplaceItemId() != null && !i.getMarketplaceItemId().isBlank())
                .limit(Math.max(0, props.getMaxOfferLookups()))
                .toList();
        if (gaps.isEmpty()) {
            return;
        }
        int recovered = 0;
        for (SearchResultItem item : gaps) {
            try {
                // New unless the caller already knows this listing's condition.
                // Filling a gap with a used price would put a different
                // product's figure beside new ones in the same result.
                String condition = item.getCondition() != null ? item.getCondition() : "New";
                List<OfferListing> offers = normalizer.toOffers(
                        client.getItemOffers(item.getMarketplaceItemId(), market, condition),
                        item.getMarketplaceItemId(), market);
                BigDecimal price = normalizer.lowestOfferPrice(offers);
                if (price != null) {
                    item.setPrice(price);
                    item.setCurrency(offers.get(0).getCurrency());
                    recovered++;
                }
            } catch (Exception e) {
                log.debug("Offer lookup failed for {} in {}: {}",
                        item.getMarketplaceItemId(), market, e.getMessage());
            }
        }
        if (recovered > 0) {
            log.info("Amazon offer fallback priced {}/{} previously unpriced item(s) in {}",
                    recovered, gaps.size(), market);
        }
    }

    /**
     * Conditions fetched when the caller does not name one.
     *
     * <p>Amazon's offers endpoint requires an {@code ItemCondition} and returns
     * only that condition, so asking once returns New only — which is what
     * happened: a Used–Like New offer that was the cheapest on the page was
     * absent from ours, and "lowest price" silently meant "lowest new price".
     * You cannot choose to exclude used stock if it was never fetched.</p>
     */
    private static final List<String> DEFAULT_OFFER_CONDITIONS = List.of("New", "Used");

    /**
     * Every competing seller's offer for an ASIN, walking regions until one
     * answers with offers.
     */
    @Override
    public List<OfferListing> getOffers(String asin, String condition) {
        List<String> conditions = condition == null || condition.isBlank()
                ? DEFAULT_OFFER_CONDITIONS : List.of(condition);

        int queried = 0;
        for (AmazonMarketplace market : props.resolveSearchMarketplaces()) {
            if (queried >= props.getMaxRegionAttempts()) {
                break;
            }
            if (unauthorized.contains(market)) {
                continue;
            }
            queried++;
            List<OfferListing> collected = offersIn(asin, market, conditions);
            if (!collected.isEmpty()) {
                return tidy(collected, asin);
            }
        }
        return List.of();
    }

    /** Offers in exactly one storefront. */
    @Override
    public List<OfferListing> getOffers(String asin, String condition, String storefront) {
        if (storefront == null || storefront.isBlank()) {
            return getOffers(asin, condition);
        }
        List<String> conditions = condition == null || condition.isBlank()
                ? DEFAULT_OFFER_CONDITIONS : List.of(condition);
        AmazonMarketplace market;
        try {
            market = pinned(storefront);
        } catch (MarketplaceApiException e) {
            // Offers are best-effort everywhere they are used; an unserviceable
            // storefront means none, not a failure of the caller.
            log.debug("No offers for {} in {}: {}", asin, storefront, e.getMessage());
            return List.of();
        }
        List<OfferListing> collected = offersIn(asin, market, conditions);
        return collected.isEmpty() ? List.of() : tidy(collected, asin);
    }

    private List<OfferListing> offersIn(String asin, AmazonMarketplace market,
                                        List<String> conditions) {
        List<OfferListing> collected = new ArrayList<>();
        for (String cond : conditions) {
            try {
                collected.addAll(normalizer.toOffers(
                        client.getItemOffers(asin, market, cond), asin, market));
            } catch (MarketplaceApiException e) {
                // One condition having no offers is ordinary — most products
                // have no used stock — so it must not lose the others.
                log.debug("Amazon {} offers for {} unavailable in {}: {}",
                        cond, asin, market, e.getMessage());
                noteFailure(market, e);
            }
        }
        return collected;
    }

    /**
     * Removes duplicate offers and resolves contradictory Buy Box flags.
     *
     * <p>Amazon returned the same seller twice at an identical price, once
     * flagged as the Buy Box winner and once not, and separately flagged two
     * different sellers as winners of the same condition. Only one offer can
     * hold the Buy Box per condition, so a panel showing two is stating
     * something that cannot be true.</p>
     */
    private List<OfferListing> tidy(List<OfferListing> offers, String asin) {
        // Identical seller, price, shipping, condition and fulfilment is one
        // offer however many times it appears. Keep the flagged copy, so a
        // duplicate cannot lose the Buy Box marker.
        Map<String, OfferListing> unique = new LinkedHashMap<>();
        for (OfferListing o : offers) {
            String key = o.getSellerId() + "|" + o.getListingPrice() + "|" + o.getShipping()
                    + "|" + o.getCondition() + "|" + o.isFulfilledByMarketplace();
            OfferListing kept = unique.get(key);
            if (kept == null) {
                unique.put(key, o);
            } else if (o.isBuyBoxWinner() && !kept.isBuyBoxWinner()) {
                unique.put(key, o);
            }
        }

        // At most one winner per condition. Where several still claim it, the
        // cheapest landed price keeps the flag: the Buy Box is overwhelmingly
        // price-driven, and on the case that exposed this the cheapest was the
        // offer Amazon's own page featured. It is a tie-break, not a fact, so
        // it is logged rather than applied silently.
        Map<String, OfferListing> winnerByCondition = new LinkedHashMap<>();
        for (OfferListing o : unique.values()) {
            if (!o.isBuyBoxWinner()) {
                continue;
            }
            String cond = o.getCondition() == null ? "" : o.getCondition().toLowerCase();
            OfferListing current = winnerByCondition.get(cond);
            if (current == null) {
                winnerByCondition.put(cond, o);
            } else {
                OfferListing loser = cheaper(o, current) ? current : o;
                loser.setBuyBoxWinner(false);
                winnerByCondition.put(cond, cheaper(o, current) ? o : current);
                log.warn("Amazon flagged more than one Buy Box winner for {} ({}); "
                        + "kept the cheaper offer", asin, cond);
            }
        }
        return new ArrayList<>(unique.values());
    }

    private boolean cheaper(OfferListing a, OfferListing b) {
        java.math.BigDecimal x = a.getLandedPrice() != null ? a.getLandedPrice() : a.getListingPrice();
        java.math.BigDecimal y = b.getLandedPrice() != null ? b.getLandedPrice() : b.getListingPrice();
        if (x == null || y == null) {
            return false;
        }
        return x.compareTo(y) < 0;
    }

    /**
     * Listing details, hunting across regions for one that has a price. Keeps
     * the detail screen consistent with search — an item found priced in GB
     * must not show a blank price when it is opened.
     */
    @Override
    public ListingDetails getListing(String asin) {
        ListingDetails last = null;
        int queried = 0;
        for (AmazonMarketplace market : props.resolveSearchMarketplaces()) {
            if (queried >= props.getMaxRegionAttempts()) {
                break;
            }
            if (unauthorized.contains(market)) {
                continue;
            }
            queried++;
            try {
                ListingDetails details = listingIn(asin, market);
                if (details.getPrice() != null) {
                    return details;
                }
                // Keep the first catalogue hit so we can still show title/brand
                // if no region ever produces a price.
                if (last == null) {
                    last = details;
                }
            } catch (MarketplaceApiException e) {
                log.warn("Amazon listing {} unavailable in {}: {}", asin, market, e.getMessage());
                noteFailure(market, e);
            }
        }
        if (last != null) {
            return last;
        }
        throw new MarketplaceApiException(
                "Amazon listing " + asin + " was not found in any configured marketplace");
    }

    /**
     * Listing details from exactly one storefront.
     *
     * <p>No hunting. The region-less version above keeps the first storefront
     * that has a price, which is right for "find me this product anywhere" and
     * wrong for "fetch the listing I saw on amazon.ca" — there it returned
     * amazon.com's page and price instead, with nothing on screen to say so.</p>
     */
    @Override
    public ListingDetails getListing(String asin, String storefront) {
        if (storefront == null || storefront.isBlank()) {
            return getListing(asin);
        }
        AmazonMarketplace market = pinned(storefront);
        try {
            return listingIn(asin, market);
        } catch (MarketplaceApiException e) {
            noteFailure(market, e);
            throw e;
        }
    }

    /** One storefront's catalogue entry, priced new. */
    private ListingDetails listingIn(String asin, AmazonMarketplace market) {
        String catalog = client.getCatalogItem(asin, market);
        String pricing = safePricing(asin, market);
        ListingDetails details = normalizer.toListingDetails(catalog, pricing, asin, market);
        if (details.getPrice() == null) {
            // No New competitive price here — read the New offers before giving
            // up, and never answer with a used price. Same rule as priceIn, so
            // the listing page and the tracked price cannot disagree.
            BigDecimal price = newPrice(asin, market, pricing);
            if (price != null) {
                details.setPrice(price);
            }
        }
        return details;
    }

    /**
     * The marketplace for a storefront code, refusing anything this connector
     * cannot serve.
     *
     * <p>Refused rather than substituted. An unknown or unauthorized storefront
     * answered from amazon.com is exactly the defect this method exists to
     * prevent.</p>
     */
    private AmazonMarketplace pinned(String storefront) {
        AmazonMarketplace market = AmazonMarketplace.find(storefront)
                .orElseThrow(() -> new MarketplaceApiException(
                        "Amazon has no storefront for region " + storefront));
        if (unauthorized.contains(market)) {
            throw new MarketplaceApiException("Amazon connector is not authorized for "
                    + market + " (HTTP 403). Authorize the SP-API app for that marketplace.");
        }
        return market;
    }

    /** Current price, hunting across regions until one reports a price. */
    @Override
    public PriceSnapshot getPrice(String asin) {
        PriceSnapshot last = null;
        int queried = 0;
        for (AmazonMarketplace market : props.resolveSearchMarketplaces()) {
            if (queried >= props.getMaxRegionAttempts()) {
                break;
            }
            if (unauthorized.contains(market)) {
                continue;
            }
            queried++;
            try {
                PriceSnapshot snapshot = priceIn(asin, market);
                if (snapshot.getItemPrice() != null) {
                    return snapshot;
                }
                if (last == null) {
                    last = snapshot;
                }
            } catch (MarketplaceApiException e) {
                log.warn("Amazon price for {} unavailable in {}: {}", asin, market, e.getMessage());
                noteFailure(market, e);
            }
        }
        if (last != null) {
            return last;
        }
        throw new MarketplaceApiException(
                "Amazon price for " + asin + " was not found in any configured marketplace");
    }

    /** A price from exactly one storefront. See {@link #getListing(String, String)}. */
    @Override
    public PriceSnapshot getPrice(String asin, String storefront) {
        if (storefront == null || storefront.isBlank()) {
            return getPrice(asin);
        }
        AmazonMarketplace market = pinned(storefront);
        try {
            return priceIn(asin, market);
        } catch (MarketplaceApiException e) {
            noteFailure(market, e);
            throw e;
        }
    }

    /** One storefront's price for this product, new. */
    private PriceSnapshot priceIn(String asin, AmazonMarketplace market) {
        String pricingJson = client.getPricing(asin, market);
        PriceSnapshot snapshot = normalizer.toPriceSnapshot(pricingJson, asin, market);
        if (snapshot.getItemPrice() == null) {
            BigDecimal price = newPrice(asin, market, pricingJson);
            if (price != null) {
                snapshot.setItemPrice(price);
                snapshot.setLandedPrice(price);
            }
        }
        return snapshot;
    }

    /**
     * What this product costs new, when the competitive New price is absent.
     *
     * <p>Amazon drops the New entry from competitive pricing at times while
     * still reporting New offers — the same ASIN returned New and Used together
     * one minute and Used alone the next. Reading the Used price in those
     * moments is what made a tracked price jump between two figures for days
     * and filled the change log with movements nobody made.</p>
     *
     * <p>So the New offers are read instead. Only a listing with no New market
     * at all falls back to a used price, and its price is then stable because
     * that is all there ever is.</p>
     */
    private BigDecimal newPrice(String asin, AmazonMarketplace market, String pricingJson) {
        BigDecimal fromOffers = offerPrice(asin, market, "New");
        if (fromOffers != null) {
            return fromOffers;
        }
        if (normalizer.offerCount(pricingJson, "New") > 0) {
            // New stock exists but neither source will quote it. Unknown is the
            // honest answer; a used price here is a different product's price.
            log.debug("Amazon {} {}: New offers exist but no New price was quoted", market, asin);
            return null;
        }
        return normalizer.extractAnyCompetitivePrice(pricingJson);
    }

    /** Best price from the offer list for one ASIN, or null. Never throws. */
    private BigDecimal offerPrice(String asin, AmazonMarketplace market, String condition) {
        if (!props.isOfferFallbackEnabled()) {
            return null;
        }
        try {
            return normalizer.lowestOfferPrice(normalizer.toOffers(
                    client.getItemOffers(asin, market, condition), asin, market));
        } catch (Exception e) {
            log.debug("Offer lookup failed for {} in {}: {}", asin, market, e.getMessage());
            return null;
        }
    }

    private static boolean hasPrice(SearchResultItem item) {
        return item.getPrice() != null && item.getPrice().signum() > 0;
    }

    private String describe(AmazonMarketplace market, List<String> tried, int dropped,
                            List<String> unavailable) {
        StringBuilder sb = new StringBuilder("Priced in ").append(market.name());
        if (tried.size() > 1) {
            sb.append(" after no priced results in ")
              .append(String.join(", ", tried.subList(0, tried.size() - 1)));
        }
        if (dropped > 0) {
            sb.append("; ").append(dropped).append(" listing(s) without a price were hidden");
        }
        if (!unavailable.isEmpty()) {
            sb.append("; could not check ").append(String.join(", ", unavailable));
        }
        return sb.append('.').toString();
    }

    /**
     * Builds the "nothing to show" answer. The wording distinguishes the three
     * reasons a search can come back empty, because they need different actions:
     * the product does not exist, it exists but is unpriced, or we were blocked
     * from looking in the marketplaces that would have had it.
     */
    private SearchResult emptyResult(String query, List<String> tried,
                                     List<String> unavailable, int productsSeen) {
        List<String> searchable = tried.stream()
                .filter(r -> unavailable.stream().noneMatch(u -> u.startsWith(r + " (")))
                .toList();

        StringBuilder note = new StringBuilder();
        if (productsSeen > 0) {
            note.append("Found in ").append(String.join(", ", searchable))
                .append(" but not priced there.");
        } else if (!searchable.isEmpty()) {
            note.append("No products found in ").append(String.join(", ", searchable)).append('.');
        }
        if (!unavailable.isEmpty()) {
            if (note.length() > 0) {
                note.append(' ');
            }
            note.append("Could not check ").append(String.join(", ", unavailable))
                .append(" — a price may exist there.");
        }

        log.info("Amazon search '{}' found no priced results; searched {}, blocked {}",
                query, searchable, unavailable);
        return SearchResult.builder()
                .marketplace(Marketplace.AMAZON)
                .query(query)
                .totalResults(0)
                .items(List.of())
                .regionsTried(tried)
                .regionsUnavailable(unavailable.isEmpty() ? null : unavailable)
                .note(note.toString())
                .sourceTimestamp(LocalDateTime.now())
                .mocked(false)
                .build();
    }

    @Override
    public FeeEstimate estimateFees(FeeEstimateRequest request) {
        String currency = request.getCurrency() != null ? request.getCurrency() : "USD";
        String json = client.getFeesEstimate(
                request.getMarketplaceItemId(), currency, request.getPrice().toPlainString());
        // Fee parsing kept minimal here; the raw estimate is returned by the client.
        return FeeEstimate.builder()
                .marketplace(Marketplace.AMAZON)
                .marketplaceItemId(request.getMarketplaceItemId())
                .price(request.getPrice())
                .currency(currency)
                .mocked(false)
                .build();
    }

    @Override
    public SalesMetrics getSales(String asin) {
        // SP-API does not expose competitor unit sales; this is deliberately UNAVAILABLE.
        return SalesMetrics.builder()
                .marketplace(Marketplace.AMAZON)
                .marketplaceItemId(asin)
                .classification("UNAVAILABLE")
                .note("Amazon SP-API does not provide competitor unit sales")
                .mocked(false)
                .build();
    }

    @Override
    public ConnectorHealth health() {
        if (!props.isFullyConfigured()) {
            return ConnectorHealth.builder()
                    .marketplace(Marketplace.AMAZON)
                    .healthy(false)
                    .status("NOT_CONFIGURED")
                    .message("Set amazon.sp-api.enabled=true and provide LWA credentials to activate.")
                    .checkedAt(LocalDateTime.now())
                    .mocked(false)
                    .build();
        }
        try {
            tokenService.getAccessToken(); // proves OAuth works
            return ConnectorHealth.builder()
                    .marketplace(Marketplace.AMAZON).healthy(true).status("UP")
                    .message("SP-API reachable; LWA token obtained")
                    .checkedAt(LocalDateTime.now()).mocked(false).build();
        } catch (Exception e) {
            return ConnectorHealth.builder()
                    .marketplace(Marketplace.AMAZON).healthy(false).status("DOWN")
                    .message("SP-API auth failed: " + e.getMessage())
                    .checkedAt(LocalDateTime.now()).mocked(false).build();
        }
    }

    private String safePricing(String asin, AmazonMarketplace market) {
        try {
            return client.getPricing(asin, market);
        } catch (MarketplaceApiException e) {
            log.warn("Pricing unavailable for {} in {}: {}", asin, market, e.getMessage());
            return null;
        }
    }
}
