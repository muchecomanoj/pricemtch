package com.priceintel.backend.marketplace.amazon;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceintel.backend.constants.IdentifierType;
import com.priceintel.backend.dto.request.CreateProductRequest;
import com.priceintel.backend.dto.request.ProductAttributeRequest;
import com.priceintel.backend.dto.request.ProductIdentifierRequest;
import com.priceintel.backend.exception.MarketplaceApiException;
import com.priceintel.backend.marketplace.Marketplace;
import com.priceintel.backend.marketplace.model.ItemIdentifier;
import com.priceintel.backend.marketplace.model.ListingDetails;
import com.priceintel.backend.marketplace.model.OfferListing;
import com.priceintel.backend.marketplace.model.PriceSnapshot;
import com.priceintel.backend.marketplace.model.SearchResult;
import com.priceintel.backend.marketplace.model.SearchResultItem;

import lombok.RequiredArgsConstructor;

/**
 * Converts Amazon SP-API JSON into our canonical models. Parsing is defensive:
 * missing fields degrade to null rather than throwing.
 */
@Component
@RequiredArgsConstructor
public class AmazonProductNormalizer {

    private final ObjectMapper objectMapper;

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json == null ? "{}" : json);
        } catch (Exception e) {
            throw new MarketplaceApiException("Could not parse Amazon response JSON: " + e.getMessage(), e);
        }
    }

    /**
     * The product codes Amazon states for one item, ASIN first.
     *
     * <p>Amazon nests these per marketplace — {@code identifiers[].identifiers[]}
     * — because the same ASIN can carry different barcodes in different regions.
     * Only the block for the marketplace we queried is read; taking them all
     * would attach a European EAN to a US listing.</p>
     *
     * <p>The ASIN is included alongside the barcodes so one list answers the
     * whole question rather than making a caller stitch it together.</p>
     */
    public List<ItemIdentifier> toIdentifiers(JsonNode itemNode, String asin,
                                              AmazonMarketplace market) {
        List<ItemIdentifier> out = new ArrayList<>();
        if (asin != null && !asin.isBlank()) {
            out.add(ItemIdentifier.builder().type("ASIN").value(asin).build());
        }
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (JsonNode block : itemNode.path("identifiers")) {
            String blockMarket = block.path("marketplaceId").asText(null);
            if (blockMarket != null && market != null
                    && !blockMarket.equals(market.getMarketplaceId())) {
                continue;
            }
            for (JsonNode id : block.path("identifiers")) {
                String type = id.path("identifierType").asText("");
                String value = id.path("identifier").asText("");
                if (type.isBlank() || value.isBlank()) {
                    continue;
                }
                // Amazon repeats a code across package variations; one row each.
                if (seen.add(type.toUpperCase() + '|' + value)) {
                    out.add(ItemIdentifier.builder()
                            .type(type.toUpperCase()).value(value).build());
                }
            }
        }
        return out;
    }

    /** Builds the canonical product (to be persisted) from a Catalog Items payload. */
    public CreateProductRequest toCreateProductRequest(String catalogJson, String asin) {
        JsonNode root = parse(catalogJson);
        JsonNode summary = firstSummary(root);

        String title = text(summary, "itemName", "Amazon product " + asin);
        String brand = text(summary, "brand", null);

        List<ProductIdentifierRequest> identifiers = new ArrayList<>();
        identifiers.add(ProductIdentifierRequest.builder()
                .type(IdentifierType.ASIN).originalValue(asin).build());
        for (JsonNode idBlock : root.path("identifiers")) {
            for (JsonNode id : idBlock.path("identifiers")) {
                String type = id.path("identifierType").asText("");
                String value = id.path("identifier").asText("");
                mapIdentifierType(type).ifPresent(t -> identifiers.add(
                        ProductIdentifierRequest.builder().type(t).originalValue(value).build()));
            }
        }

        List<ProductAttributeRequest> attributes = new ArrayList<>();
        String color = text(summary, "colorName", null);
        String size = text(summary, "sizeName", null);
        String model = text(summary, "modelNumber", null);
        if (color != null || size != null || model != null) {
            attributes.add(ProductAttributeRequest.builder()
                    .color(color).size(size).model(model).build());
        }

        return CreateProductRequest.builder()
                .sku(asin) // ASIN as the canonical SKU for Amazon-sourced products
                .title(title)
                .brand(brand)
                .category(text(summary, "browseClassification", null))
                .identifiers(identifiers)
                .attributes(attributes.isEmpty() ? null : attributes)
                .build();
    }

    public SearchResult toSearchResult(String searchJson, String query) {
        return toSearchResult(searchJson, query, AmazonMarketplace.US);
    }

    /** Search results found in a specific marketplace (drives the storefront links). */
    public SearchResult toSearchResult(String searchJson, String query, AmazonMarketplace market) {
        JsonNode root = parse(searchJson);
        List<SearchResultItem> items = new ArrayList<>();
        for (JsonNode item : root.path("items")) {
            String asin = item.path("asin").asText("");
            JsonNode summary = firstSummary(item);
            items.add(SearchResultItem.builder()
                    .marketplaceItemId(asin)
                    .title(text(summary, "itemName", asin))
                    .seller(text(summary, "brand", null))
                    // Left null deliberately. The Catalog Items API describes a
                    // product, not an offer, and never states condition — so any
                    // value here is invented. NEW was the worst possible guess:
                    // it is the one value that passes the condition check
                    // silently, so "(Renewed)" listings compared as new.
                    .condition(null)
                    .url(market.productUrl(asin))
                    .storefront(market.getCountryCode())
                    .identifiers(toIdentifiers(item, asin, market))
                    .build());
        }
        return SearchResult.builder()
                .marketplace(Marketplace.AMAZON)
                .query(query)
                .totalResults(root.path("numberOfResults").asInt(items.size()))
                .items(items)
                .sourceTimestamp(LocalDateTime.now())
                .mocked(false)
                .build();
    }

    public ListingDetails toListingDetails(String catalogJson, String pricingJson, String asin) {
        return toListingDetails(catalogJson, pricingJson, asin, AmazonMarketplace.US);
    }

    /** Listing details priced in a specific marketplace (drives currency + provenance). */
    public ListingDetails toListingDetails(String catalogJson, String pricingJson, String asin,
                                           AmazonMarketplace market) {
        JsonNode root = parse(catalogJson);
        JsonNode summary = firstSummary(root);
        BigDecimal price = extractPrice(pricingJson);
        String currency = extractCurrency(pricingJson);
        return ListingDetails.builder()
                .marketplace(Marketplace.AMAZON)
                .marketplaceItemId(asin)
                .title(text(summary, "itemName", "Amazon product " + asin))
                .brand(text(summary, "brand", null))
                // The storefront page for the marketplace this was priced in, so
                // the link and the price on screen describe the same offer.
                .url(market.productUrl(asin))
                .identifiers(toIdentifiers(root, asin, market))
                .available(true)
                .price(price)
                // Prefer the currency Amazon reported; fall back to the marketplace's
                // own currency so a GBP listing is never mislabelled as USD.
                .currency(currency != null ? currency : market.getCurrency())
                .countryCode(market.getCountryCode())
                .marketplaceId(market.getMarketplaceId())
                .sourceTimestamp(LocalDateTime.now())
                .mocked(false)
                .build();
    }

    public PriceSnapshot toPriceSnapshot(String pricingJson, String asin) {
        return toPriceSnapshot(pricingJson, asin, AmazonMarketplace.US);
    }

    /** Price snapshot observed in a specific marketplace. */
    public PriceSnapshot toPriceSnapshot(String pricingJson, String asin, AmazonMarketplace market) {
        BigDecimal price = extractPrice(pricingJson);
        String currency = extractCurrency(pricingJson);
        return PriceSnapshot.builder()
                .marketplace(Marketplace.AMAZON)
                .marketplaceItemId(asin)
                .itemPrice(price)
                // competitivePrice does not report shipping. Null says "unknown";
                // zero would assert free delivery we have no evidence for.
                .shipping(null)
                .landedPrice(price)
                .currency(currency != null ? currency : market.getCurrency())
                .countryCode(market.getCountryCode())
                .marketplaceId(market.getMarketplaceId())
                .observedAt(LocalDateTime.now())
                .mocked(false)
                .build();
    }

    /**
     * Parses a getItemOffers payload into the competing sellers' offers.
     *
     * <p>Note the shape difference from competitivePrice: there {@code payload}
     * is an array of ASIN entries, here it is a single object for one ASIN.</p>
     */
    public List<OfferListing> toOffers(String offersJson, String asin, AmazonMarketplace market) {
        List<OfferListing> offers = new ArrayList<>();
        if (offersJson == null) {
            return offers;
        }
        JsonNode payload = parse(offersJson).path("payload");
        for (JsonNode o : payload.path("Offers")) {
            BigDecimal listing = money(o.path("ListingPrice"));
            BigDecimal shipping = money(o.path("Shipping"));
            if (listing == null) {
                continue;
            }
            JsonNode feedback = o.path("SellerFeedbackRating");
            offers.add(OfferListing.builder()
                    .marketplace(Marketplace.AMAZON)
                    .marketplaceItemId(asin)
                    .sellerId(text(o, "SellerId", null))
                    .condition(text(o, "SubCondition", payload.path("ItemCondition").asText(null)))
                    .listingPrice(listing)
                    .shipping(shipping)
                    .landedPrice(shipping == null ? listing : listing.add(shipping))
                    .currency(currencyOf(o.path("ListingPrice"), market))
                    .buyBoxWinner(o.path("IsBuyBoxWinner").asBoolean(false))
                    .fulfilledByMarketplace(o.path("IsFulfilledByAmazon").asBoolean(false))
                    .sellerPositiveFeedbackPercent(
                            feedback.path("SellerPositiveFeedbackRating").isNumber()
                                    ? feedback.path("SellerPositiveFeedbackRating").asDouble() : null)
                    .sellerFeedbackCount(
                            feedback.path("FeedbackCount").isNumber()
                                    ? feedback.path("FeedbackCount").asInt() : null)
                    .countryCode(market.getCountryCode())
                    .build());
        }
        // Cheapest landed price first — the competitor that matters most.
        offers.sort(java.util.Comparator.comparing(
                OfferListing::getLandedPrice, java.util.Comparator.nullsLast(BigDecimal::compareTo)));
        return offers;
    }

    /**
     * The best price obtainable from an offers payload: the Buy Box winner if
     * there is one, otherwise the cheapest landed offer. This is what makes the
     * offers call worth its cost — it yields a price where competitivePrice,
     * which only reports the featured offer, returns nothing.
     */
    public BigDecimal lowestOfferPrice(List<OfferListing> offers) {
        return offers.stream()
                .filter(o -> o.getListingPrice() != null)
                .sorted(java.util.Comparator.comparing(OfferListing::isBuyBoxWinner).reversed())
                .map(OfferListing::getListingPrice)
                .findFirst()
                .orElse(null);
    }

    private BigDecimal money(JsonNode node) {
        JsonNode amount = node.path("Amount");
        return amount.isNumber() ? amount.decimalValue() : null;
    }

    private String currencyOf(JsonNode priceNode, AmazonMarketplace market) {
        JsonNode c = priceNode.path("CurrencyCode");
        return c.isTextual() ? c.asText() : market.getCurrency();
    }

    /** Map of ASIN → competitive price from a multi-ASIN pricing response. */
    public java.util.Map<String, BigDecimal> extractPricesByAsin(String pricingJson) {
        java.util.Map<String, BigDecimal> map = new java.util.HashMap<>();
        if (pricingJson == null) {
            return map;
        }
        JsonNode root = parse(pricingJson);
        for (JsonNode entry : root.path("payload")) {
            String asin = entry.path("ASIN").asText(null);
            if (asin == null) {
                continue;
            }
            // The New price only, as the single-item read does. Taking the
            // first entry meant a search and a listing page could disagree
            // about one ASIN, and that a repeated search recorded a change
            // that never happened. A missing New entry leaves the ASIN unpriced
            // here; the caller fills it from New offers.
            JsonNode best = null;
            String bestId = null;
            for (JsonNode cp : entry.path("Product").path("CompetitivePricing").path("CompetitivePrices")) {
                if (!cp.path("Price").path("ListingPrice").path("Amount").isNumber()
                        || !"New".equalsIgnoreCase(cp.path("condition").asText(""))) {
                    continue;
                }
                String id = cp.path("CompetitivePriceId").asText("");
                if (best == null || id.compareTo(bestId) < 0) {
                    best = cp;
                    bestId = id;
                }
            }
            if (best != null) {
                map.put(asin, best.path("Price").path("ListingPrice").path("Amount").decimalValue());
            }
        }
        return map;
    }

    /**
     * The currency of the price that was chosen, so the two always agree.
     *
     * <p>Falls back to any condition's entry, because a listing priced only in
     * Used still has a currency worth reporting.</p>
     */
    public String extractCurrency(String pricingJson) {
        JsonNode chosen = chooseCompetitivePrice(pricingJson, true);
        if (chosen == null) {
            chosen = chooseCompetitivePrice(pricingJson, false);
        }
        if (chosen != null) {
            JsonNode c = chosen.path("Price").path("ListingPrice").path("CurrencyCode");
            if (c.isTextual()) {
                return c.asText();
            }
        }
        if (pricingJson == null) {
            return null;
        }
        for (JsonNode entry : parse(pricingJson).path("payload")) {
            for (JsonNode offer : entry.path("Product").path("Offers")) {
                JsonNode c = offer.path("BuyingPrice").path("ListingPrice").path("CurrencyCode");
                if (c.isTextual()) {
                    return c.asText();
                }
            }
        }
        return null;
    }

    /** Extracts the first available listing price from a Pricing v0 payload. */
    /**
     * The competitive price a listing should be tracked at.
     *
     * <p>Amazon returns <em>several</em> competitive prices per ASIN — one per
     * condition, each with a {@code CompetitivePriceId}: id 1 is the New Buy
     * Box, id 2 the Used one. For B0DGHMNQ5Z it sends New $99.00 and Used
     * $92.18 together.</p>
     *
     * <p>This used to return whichever appeared first in the array, and that
     * order is not stable: two calls a minute apart returned $99.00 and then
     * $92.18 for an unchanged listing. Every flip was then recorded as a real
     * 7% price move, firing alerts and feeding recommendations. 151 changes
     * were logged in a week on a handful of products, most of them this.</p>
     *
     * <p>Worse, the array's <em>contents</em> vary too: the same ASIN came back
     * with New and Used together one minute and with Used alone the next, while
     * Amazon reported five New offers either way. Substituting the Used price
     * whenever the New entry is missing reproduces the phantom changes exactly.</p>
     *
     * <p>So this returns the <b>New</b> price and nothing else. A missing New
     * entry is missing information, not a cheaper product, and the caller looks
     * to New <em>offers</em> next — see {@code AmazonAdapter}. Only a listing
     * with no New market at all falls back to
     * {@link #extractAnyCompetitivePrice}.</p>
     */
    public BigDecimal extractPrice(String pricingJson) {
        JsonNode chosen = chooseCompetitivePrice(pricingJson, true);
        if (chosen != null) {
            JsonNode amount = chosen.path("Price").path("ListingPrice").path("Amount");
            if (amount.isNumber()) {
                return amount.decimalValue();
            }
        }
        // The requester's own offer, for a seller looking at their own product.
        // After the competitive prices, because this is a competitor tracker and
        // our own listing price is not the market's.
        for (JsonNode entry : parse(pricingJson == null ? "{}" : pricingJson).path("payload")) {
            for (JsonNode offer : entry.path("Product").path("Offers")) {
                JsonNode amount = offer.path("BuyingPrice").path("ListingPrice").path("Amount");
                if (amount.isNumber()) {
                    return amount.decimalValue();
                }
            }
        }
        return null;
    }

    /**
     * A competitive price of any condition, chosen deterministically.
     *
     * <p>The last resort, for a listing whose market really is used-only. Any
     * rule would do as long as it is the same rule every time — an arbitrary
     * pick is what produced phantom price changes — so it is the lowest
     * {@code CompetitivePriceId}.</p>
     */
    public BigDecimal extractAnyCompetitivePrice(String pricingJson) {
        JsonNode chosen = chooseCompetitivePrice(pricingJson, false);
        return chosen == null ? null
                : chosen.path("Price").path("ListingPrice").path("Amount").isNumber()
                        ? chosen.path("Price").path("ListingPrice").path("Amount").decimalValue()
                        : null;
    }

    /** How many offers Amazon reports in a condition, or 0 when it says nothing. */
    public int offerCount(String pricingJson, String condition) {
        if (pricingJson == null) {
            return 0;
        }
        for (JsonNode entry : parse(pricingJson).path("payload")) {
            for (JsonNode c : entry.path("Product").path("CompetitivePricing")
                    .path("NumberOfOfferListings")) {
                if (condition.equalsIgnoreCase(c.path("condition").asText(""))) {
                    return c.path("Count").asInt(0);
                }
            }
        }
        return 0;
    }

    /**
     * The competitive price to read, or null when the payload has none.
     *
     * @param newOnly true to consider only New entries — a missing New price is
     *                then reported as unknown rather than answered with a
     *                different condition's price
     */
    private JsonNode chooseCompetitivePrice(String pricingJson, boolean newOnly) {
        if (pricingJson == null) {
            return null;
        }
        JsonNode best = null;
        String bestId = null;
        boolean bestIsNew = false;
        for (JsonNode entry : parse(pricingJson).path("payload")) {
            for (JsonNode cp : entry.path("Product").path("CompetitivePricing")
                    .path("CompetitivePrices")) {
                if (!cp.path("Price").path("ListingPrice").path("Amount").isNumber()) {
                    continue;
                }
                boolean isNew = "New".equalsIgnoreCase(cp.path("condition").asText(""));
                if (newOnly && !isNew) {
                    continue;
                }
                String id = cp.path("CompetitivePriceId").asText("");
                if (best == null
                        || (isNew && !bestIsNew)
                        || (isNew == bestIsNew && id.compareTo(bestId) < 0)) {
                    best = cp;
                    bestId = id;
                    bestIsNew = isNew;
                }
            }
        }
        return best;
    }

    // ---------- helpers ----------

    private JsonNode firstSummary(JsonNode node) {
        JsonNode summaries = node.path("summaries");
        return summaries.isArray() && !summaries.isEmpty() ? summaries.get(0) : objectMapper.createObjectNode();
    }

    private String text(JsonNode node, String field, String defaultValue) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() || v.asText().isBlank() ? defaultValue : v.asText();
    }

    private java.util.Optional<IdentifierType> mapIdentifierType(String type) {
        return switch (type == null ? "" : type.toUpperCase()) {
            case "UPC" -> java.util.Optional.of(IdentifierType.UPC);
            case "EAN" -> java.util.Optional.of(IdentifierType.EAN);
            case "GTIN" -> java.util.Optional.of(IdentifierType.GTIN);
            default -> java.util.Optional.empty();
        };
    }
}
