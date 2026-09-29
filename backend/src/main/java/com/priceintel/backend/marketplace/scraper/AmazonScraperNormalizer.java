package com.priceintel.backend.marketplace.scraper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceintel.backend.exception.MarketplaceApiException;
import com.priceintel.backend.marketplace.Marketplace;
import com.priceintel.backend.marketplace.amazon.AmazonMarketplace;
import com.priceintel.backend.marketplace.model.ItemIdentifier;
import com.priceintel.backend.marketplace.model.SearchResult;
import com.priceintel.backend.marketplace.model.SearchResultItem;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Turns the scraper's product JSON into one search result. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AmazonScraperNormalizer {

    /**
     * Detail rows that hold a product code. The service returns the listing's
     * technical-detail table as free-form keys, which vary per product.
     */
    private static final Map<String, String> IDENTIFIER_KEYS = Map.of(
            "upc", "UPC",
            "ean", "EAN",
            "gtin", "GTIN",
            "part number", "MPN",
            "model number", "MPN",
            "item model number", "MPN");

    private final ObjectMapper objectMapper;

    /**
     * @param requestedCountry the storefront the caller asked for, used only to
     *                         notice when the service answered from another one
     */
    public SearchResult toSearchResult(String json, String asin, String requestedCountry,
                                       boolean acceptConverted) {
        SearchResult.SearchResultBuilder result = SearchResult.builder()
                .marketplace(Marketplace.AMAZON)
                .query(asin)
                .sourceTimestamp(LocalDateTime.now())
                .mocked(false);
        if (json == null) {
            return result.items(new ArrayList<>()).totalResults(0)
                    .note("The scraper found " + asin + " on none of its marketplaces.").build();
        }
        JsonNode root = parse(json);
        if (!root.path("found").asBoolean(true)) {
            return result.items(new ArrayList<>()).totalResults(0)
                    .note("The scraper found " + asin + " on none of its marketplaces.").build();
        }

        // Where it actually answered from. The service treats `domain` as a
        // starting point and falls through to other marketplaces, so a request
        // for the UK can come back from Germany. Recording the requested
        // storefront would file German data under amazon.co.uk.
        String servedCountry = AmazonScraperClient.countryForDomain(
                root.path("marketplace").asText(null));
        AmazonMarketplace market = servedCountry == null ? null
                : AmazonMarketplace.find(servedCountry).orElse(null);

        // The listing id Amazon actually served, which can be a parent or a
        // variant of the one asked for.
        String listingAsin = text(root, "listing_asin");
        String itemId = listingAsin != null ? listingAsin : text(root, "asin");

        List<String> notes = new ArrayList<>();
        if (servedCountry != null && requestedCountry != null
                && !servedCountry.equalsIgnoreCase(requestedCountry)) {
            notes.add("Asked for Amazon " + requestedCountry + ", answered by Amazon "
                    + servedCountry + ".");
        }
        if (listingAsin != null && !listingAsin.equalsIgnoreCase(text(root, "asin"))) {
            notes.add("Amazon served listing " + listingAsin + " for " + asin
                    + " — a parent or variant of it.");
        }

        Price price = price(root.path("price"), root.path("price_status").asText(""),
                acceptConverted, notes);

        SearchResultItem item = SearchResultItem.builder()
                .marketplaceItemId(itemId)
                .title(text(root, "product_name"))
                .marketplace(Marketplace.AMAZON.name())
                .storefront(servedCountry)
                .url(market != null ? market.productUrl(itemId) : null)
                .price(price.amount())
                .currency(price.currency())
                .priceEstimated(price.estimated())
                .priceNote(price.note())
                .identifiers(identifiers(root, itemId))
                // The page states none of these, and inventing them is worse
                // than leaving them unknown: a guessed condition is the one
                // value that passes a condition check silently.
                .condition(null)
                .seller(null)
                .shipping(null)
                .availability(price.amount() != null ? "IN_STOCK" : null)
                .build();

        List<SearchResultItem> items = new ArrayList<>();
        items.add(item);
        return result.items(items).totalResults(1)
                .countryCode(servedCountry)
                .marketplaceId(market != null ? market.getMarketplaceId() : null)
                .note(notes.isEmpty() ? null : String.join(" ", notes))
                .build();
    }

    /** A usable price, whether it is an estimate, and why. */
    private record Price(BigDecimal amount, String currency, boolean estimated, String note) {

        static Price none(String currency) {
            return new Price(null, currency, false, null);
        }

        static Price observed(BigDecimal amount, String currency) {
            return new Price(amount, currency, false, null);
        }
    }

    /**
     * The price, when it is one we may trust.
     *
     * <p>Only {@code ok} — a price the storefront quoted in its own currency —
     * is used by default. {@code converted} is an import quote translated at the
     * day's rate: the service's own documentation calls it an estimate that
     * reads higher than the domestic shelf price, because it carries duty and
     * international shipping. Feeding that into competitor statistics would
     * quietly distort every median and recommendation built on them, and it
     * would be indistinguishable from an observed price afterwards.</p>
     */
    private Price price(JsonNode priceNode, String status, boolean acceptConverted,
                        List<String> notes) {
        BigDecimal amount = decimal(priceNode.path("amount"));
        String currency = text(priceNode, "currency");
        switch (status == null ? "" : status.toLowerCase(Locale.ROOT)) {
            case "ok" -> {
                return Price.observed(amount, currency);
            }
            case "converted" -> {
                String original = text(priceNode.path("original"), "currency");
                String originalAmount = text(priceNode.path("original"), "raw");
                String reason = "Estimate. Amazon quoted this listing in " + original
                        + (originalAmount == null ? "" : " (" + originalAmount + ")")
                        + " because the reader is outside that country, so the figure carries "
                        + "import duty and international shipping. It is converted at "
                        + text(priceNode.path("fx"), "rate")
                        + " and is not the price a local buyer pays.";
                if (acceptConverted) {
                    notes.add(reason);
                    return new Price(amount, currency, true, reason);
                }
                notes.add(reason + " It is left out; set "
                        + "amazon.scraper.accept-converted-prices=true to show it as an estimate.");
                return Price.none(currency);
            }
            case "localised_currency" -> {
                notes.add("Amazon quoted this listing in a foreign currency that could not be "
                        + "converted, so no price is recorded.");
                return Price.none(null);
            }
            default -> {
                notes.add("The page carries no price — out of stock, buying options only, or "
                        + "not deliverable to the reader's location.");
                return Price.none(null);
            }
        }
    }

    /** ASIN plus whatever codes the detail table states. */
    private List<ItemIdentifier> identifiers(JsonNode root, String itemId) {
        List<ItemIdentifier> out = new ArrayList<>();
        add(out, "ASIN", itemId);
        JsonNode details = root.path("details");
        for (Iterator<String> it = details.fieldNames(); it.hasNext();) {
            String field = it.next();
            String type = IDENTIFIER_KEYS.get(field.trim().toLowerCase(Locale.ROOT));
            if (type != null) {
                add(out, type, details.path(field).asText(null));
            }
        }
        return out;
    }

    private void add(List<ItemIdentifier> out, String type, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String v = value.trim();
        boolean duplicate = out.stream()
                .anyMatch(i -> i.getType().equals(type) && i.getValue().equalsIgnoreCase(v));
        if (!duplicate) {
            out.add(ItemIdentifier.builder().type(type).value(v).build());
        }
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new MarketplaceApiException(
                    "Could not parse the Amazon scraper response: " + e.getMessage(), e);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() || v.asText().isBlank() ? null : v.asText();
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
}
