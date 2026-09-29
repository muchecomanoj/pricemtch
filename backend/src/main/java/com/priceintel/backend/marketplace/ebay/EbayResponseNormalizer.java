package com.priceintel.backend.marketplace.ebay;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceintel.backend.constants.IdentifierType;
import com.priceintel.backend.dto.request.CreateProductRequest;
import com.priceintel.backend.dto.request.ProductIdentifierRequest;
import com.priceintel.backend.exception.MarketplaceApiException;
import com.priceintel.backend.marketplace.Marketplace;
import com.priceintel.backend.marketplace.model.ItemIdentifier;
import com.priceintel.backend.marketplace.model.ListingDetails;
import com.priceintel.backend.marketplace.model.PriceSnapshot;
import com.priceintel.backend.marketplace.model.SearchResult;
import com.priceintel.backend.marketplace.model.SearchResultItem;

import lombok.RequiredArgsConstructor;

/**
 * Converts eBay Browse API JSON into our canonical models. Captures the fields
 * the phase calls for: search results, listing, shipping, seller, availability.
 * Parsing is defensive — missing fields degrade to null.
 */
@Component
@RequiredArgsConstructor
public class EbayResponseNormalizer {

    private final ObjectMapper objectMapper;

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json == null ? "{}" : json);
        } catch (Exception e) {
            throw new MarketplaceApiException("Could not parse eBay response JSON: " + e.getMessage(), e);
        }
    }

    /** item_summary/search -> SearchResult. */
    public SearchResult toSearchResult(String searchJson, String query) {
        JsonNode root = parse(searchJson);
        List<SearchResultItem> items = new ArrayList<>();
        for (JsonNode it : root.path("itemSummaries")) {
            items.add(SearchResultItem.builder()
                    .marketplaceItemId(it.path("itemId").asText(null))
                    .title(it.path("title").asText(null))
                    .url(it.path("itemWebUrl").asText(null))
                    .price(decimal(it.path("price").path("value")))
                    .currency(it.path("price").path("currency").asText(null))
                    .seller(it.path("seller").path("username").asText(null))
                    .condition(it.path("condition").asText(null))
                    .shipping(firstShippingCost(it))
                    .build());
        }
        return SearchResult.builder()
                .marketplace(Marketplace.EBAY)
                .query(query)
                .totalResults(root.path("total").asInt(items.size()))
                .items(items)
                .sourceTimestamp(LocalDateTime.now())
                .mocked(false)
                .build();
    }

    /** get item -> ListingDetails (seller, condition, availability). */
    public ListingDetails toListingDetails(String itemJson) {
        JsonNode it = parse(itemJson);
        return ListingDetails.builder()
                .marketplace(Marketplace.EBAY)
                .marketplaceItemId(it.path("itemId").asText(null))
                .title(it.path("title").asText(null))
                .brand(it.path("brand").asText(null))
                .url(it.path("itemWebUrl").asText(null))
                .seller(it.path("seller").path("username").asText(null))
                .condition(it.path("condition").asText(null))
                .available(isAvailable(it))
                .price(decimal(it.path("price").path("value")))
                .currency(it.path("price").path("currency").asText(null))
                .identifiers(ebayIdentifiers(it))
                .sourceTimestamp(LocalDateTime.now())
                .mocked(false)
                .build();
    }

    /**
     * The codes eBay states on an item.
     *
     * <p>Flat fields rather than Amazon's nested per-marketplace blocks, and
     * sparser: a seller lists by hand and often supplies none. Only the item
     * endpoint carries them — search summaries do not, so a search result's
     * identifiers stay empty until the item itself is fetched.</p>
     */
    private List<ItemIdentifier> ebayIdentifiers(JsonNode it) {
        List<ItemIdentifier> out = new ArrayList<>();
        addIdentifier(out, "EPID", it.path("epid").asText(null));
        for (JsonNode gtin : it.path("gtins")) {
            addIdentifier(out, "GTIN", gtin.asText(null));
        }
        addIdentifier(out, "GTIN", it.path("gtin").asText(null));
        addIdentifier(out, "MPN", it.path("mpn").asText(null));
        return out;
    }

    private void addIdentifier(List<ItemIdentifier> out, String type, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        boolean duplicate = out.stream()
                .anyMatch(i -> i.getType().equals(type) && i.getValue().equals(value));
        if (!duplicate) {
            out.add(ItemIdentifier.builder().type(type).value(value).build());
        }
    }

    /** get item -> PriceSnapshot (item price + shipping = landed). */
    public PriceSnapshot toPriceSnapshot(String itemJson) {
        JsonNode it = parse(itemJson);
        BigDecimal item = decimal(it.path("price").path("value"));
        BigDecimal shipping = firstShippingCost(it);
        BigDecimal landed = item == null ? null
                : item.add(shipping == null ? BigDecimal.ZERO : shipping);
        return PriceSnapshot.builder()
                .marketplace(Marketplace.EBAY)
                .marketplaceItemId(it.path("itemId").asText(null))
                .itemPrice(item)
                .shipping(shipping)
                .landedPrice(landed)
                .currency(it.path("price").path("currency").asText(null))
                .observedAt(LocalDateTime.now())
                .mocked(false)
                .build();
    }

    // ---------- seller comparison ----------

    /**
     * item_summary/search -> one row per listing, with the seller's feedback.
     *
     * <p>Separate from {@link #toSearchResult} because it carries seller detail
     * the general search result does not need.</p>
     */
    public List<com.priceintel.backend.dto.response.EbayOtherSellersResponse.Seller>
            toSellerListings(String searchJson) {
        JsonNode root = parse(searchJson);
        List<com.priceintel.backend.dto.response.EbayOtherSellersResponse.Seller> rows =
                new ArrayList<>();
        for (JsonNode it : root.path("itemSummaries")) {
            rows.add(sellerRow(it));
        }
        return rows;
    }

    /** get item -> the same row shape, for the listing the comparison starts from. */
    public com.priceintel.backend.dto.response.EbayOtherSellersResponse.Seller
            toSellerListing(String itemJson) {
        return sellerRow(parse(itemJson));
    }

    /** Search summaries and full items share these fields under the same names. */
    private com.priceintel.backend.dto.response.EbayOtherSellersResponse.Seller sellerRow(JsonNode it) {
        BigDecimal price = decimal(it.path("price").path("value"));
        BigDecimal shipping = firstShippingCost(it);
        boolean shippingKnown = shipping != null;
        JsonNode seller = it.path("seller");
        Integer feedbackScore = seller.path("feedbackScore").isNumber()
                ? seller.path("feedbackScore").asInt() : null;
        // A seller with no feedback yet is reported by eBay as "0.0" percent.
        // That means unrated, not 0% positive — shown as 0% it would make a new
        // seller look like the worst on the site.
        Double feedbackPercent = feedbackScore != null && feedbackScore == 0
                ? null : doubleOrNull(seller.path("feedbackPercentage"));
        return com.priceintel.backend.dto.response.EbayOtherSellersResponse.Seller.builder()
                .itemId(textOrNull(it, "itemId"))
                .legacyItemId(textOrNull(it, "legacyItemId"))
                .title(textOrNull(it, "title"))
                .url(textOrNull(it, "itemWebUrl"))
                .seller(textOrNull(seller, "username"))
                // eBay sends the percentage as a string ("99.8").
                .feedbackPercent(feedbackPercent)
                .feedbackScore(feedbackScore)
                .condition(textOrNull(it, "condition"))
                .price(price)
                .shipping(shipping)
                .landedPrice(price != null && shippingKnown ? price.add(shipping) : null)
                .currency(it.path("price").path("currency").asText(null))
                .shippingKnown(shippingKnown)
                .listingsFromSeller(1)
                .build();
    }

    private Double doubleOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || node.asText().isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(node.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** get item -> canonical product to persist. */
    public CreateProductRequest toCreateProductRequest(String itemJson, String itemId) {
        JsonNode it = parse(itemJson);
        String title = it.path("title").asText("eBay item " + itemId);
        String brand = textOrNull(it, "brand");

        List<ProductIdentifierRequest> identifiers = new ArrayList<>();
        identifiers.add(ProductIdentifierRequest.builder()
                .type(IdentifierType.MARKETPLACE_ID).originalValue(itemId).marketplace("EBAY").build());
        addIf(identifiers, IdentifierType.GTIN, textOrNull(it, "gtin"));
        addIf(identifiers, IdentifierType.MPN, textOrNull(it, "mpn"));

        return CreateProductRequest.builder()
                .sku("EBAY-" + itemId)
                .title(title)
                .brand(brand)
                .category(textOrNull(it.path("categoryPath").isMissingNode() ? it : it, "categoryPath"))
                .identifiers(identifiers)
                .build();
    }

    // ---------- helpers ----------

    private void addIf(List<ProductIdentifierRequest> list, IdentifierType type, String value) {
        if (value != null && !value.isBlank()) {
            list.add(ProductIdentifierRequest.builder().type(type).originalValue(value).build());
        }
    }

    private boolean isAvailable(JsonNode item) {
        for (JsonNode a : item.path("estimatedAvailabilities")) {
            String status = a.path("estimatedAvailabilityStatus").asText("");
            if ("IN_STOCK".equalsIgnoreCase(status)) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal firstShippingCost(JsonNode item) {
        for (JsonNode opt : item.path("shippingOptions")) {
            JsonNode cost = opt.path("shippingCost").path("value");
            if (!cost.isMissingNode()) {
                return decimal(cost);
            }
        }
        return null;
    }

    private BigDecimal decimal(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        try {
            return new BigDecimal(node.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() || v.asText().isBlank() ? null : v.asText();
    }
}
