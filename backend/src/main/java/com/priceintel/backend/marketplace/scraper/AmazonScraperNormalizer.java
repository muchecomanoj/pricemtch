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
import com.priceintel.backend.marketplace.model.ListingDetails;
import com.priceintel.backend.marketplace.model.OfferListing;
import com.priceintel.backend.marketplace.model.PriceSnapshot;
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

    /** Marks everything the page reader produces, so the screen can say where it came from. */
    public static final String SOURCE = "PAGE_READER";

    /**
     * What the page says about one product, for the listing screen.
     *
     * <p>Thrown rather than returned empty when the product is not there: the
     * listing screen asked for one known listing, and an empty object would
     * render as a page of blanks with nothing explaining why.</p>
     */
    public ListingDetails toListingDetails(String json, String asin, String requestedCountry,
                                           boolean acceptConverted) {
        Read read = read(json, asin, requestedCountry, acceptConverted);
        return ListingDetails.builder()
                .marketplace(Marketplace.AMAZON)
                .marketplaceItemId(read.itemId())
                .title(text(read.root(), "product_name"))
                .brand(text(read.root().path("details"), "brand"))
                .url(read.market() == null ? null : read.market().productUrl(read.itemId()))
                .available(read.price().amount() != null)
                .price(read.price().amount())
                .currency(read.price().currency())
                .identifiers(identifiers(read.root(), read.itemId()))
                .sourceTimestamp(LocalDateTime.now())
                .mocked(false)
                .countryCode(read.servedCountry())
                .marketplaceId(read.market() == null ? null : read.market().getMarketplaceId())
                .priceEstimated(read.price().estimated())
                .priceNote(read.note())
                .source(SOURCE)
                // The page states neither of these, and a guessed condition is
                // the one value that passes a condition check silently.
                .condition(null)
                .seller(null)
                .build();
    }

    /** The page's price, for the listing screen's price call. */
    public PriceSnapshot toPriceSnapshot(String json, String asin, String requestedCountry,
                                         boolean acceptConverted) {
        Read read = read(json, asin, requestedCountry, acceptConverted);
        return PriceSnapshot.builder()
                .marketplace(Marketplace.AMAZON)
                .marketplaceItemId(read.itemId())
                .itemPrice(read.price().amount())
                // The page quotes one figure, delivered. Splitting it into item
                // and shipping would be inventing a breakdown it never gave.
                .landedPrice(read.price().amount())
                .currency(read.price().currency())
                .observedAt(LocalDateTime.now())
                .mocked(false)
                .countryCode(read.servedCountry())
                .marketplaceId(read.market() == null ? null : read.market().getMarketplaceId())
                .priceEstimated(read.price().estimated())
                .priceNote(read.note())
                .source(SOURCE)
                .build();
    }

    /**
     * Every seller the page lists: the buy box, then the other offers.
     *
     * <p>The page's "Other sellers on Amazon" panel names each merchant, its
     * price, its feedback and whether Amazon ships it — so this is a real seller
     * list, not a single row. An earlier version read only the buy-box price and
     * returned nothing when there was none, which hid ten live UK offers behind
     * "No competing offers" on a listing whose buy box was simply empty.</p>
     *
     * <p>Cheapest first, as the SP-API path sorts them, so the two sources read
     * alike on one screen.</p>
     */
    public List<OfferListing> toOffers(String json, String asin, String requestedCountry,
                                       boolean acceptConverted) {
        Read read = read(json, asin, requestedCountry, acceptConverted);
        List<OfferListing> offers = new ArrayList<>();

        // The featured offer. The page states no seller for it, only the price.
        if (read.price().amount() != null) {
            offers.add(offer(read, read.price(), true, null, read.price().note()));
        }
        for (JsonNode node : read.root().path("other_sellers")) {
            OfferListing other = otherSeller(read, node, acceptConverted);
            if (other != null) {
                offers.add(other);
            }
        }
        offers.sort(java.util.Comparator.comparing(
                OfferListing::getLandedPrice, java.util.Comparator.nullsLast(BigDecimal::compareTo)));
        return offers;
    }

    /**
     * One row of the page's other-sellers panel.
     *
     * <p>Null when the row carries no price we may use — a converted import
     * quote with conversions switched off, say. A seller row with no price
     * cannot be compared against anything, and an empty row in a price table
     * invites the reader to supply the missing number themselves.</p>
     */
    private OfferListing otherSeller(Read read, JsonNode node, boolean acceptConverted) {
        JsonNode priceNode = node.path("price");
        // These rows carry no price_status of their own; the `converted` flag
        // is the same distinction under another name.
        String status = priceNode.path("converted").asBoolean(false) ? "converted" : "ok";
        List<String> notes = new ArrayList<>();
        Price price = price(priceNode, status, acceptConverted, notes);
        if (price.amount() == null) {
            return null;
        }
        JsonNode seller = node.path("seller");
        return OfferListing.builder()
                .marketplace(Marketplace.AMAZON)
                .marketplaceItemId(read.itemId())
                .sellerId(text(seller, "id"))
                .sellerName(text(seller, "name"))
                .condition(text(node, "condition"))
                .listingPrice(price.amount())
                // The page quotes one delivered figure and names no shipping
                // line, so splitting it would invent a breakdown.
                .landedPrice(price.amount())
                .currency(price.currency())
                .buyBoxWinner(false)
                .fulfilledByAmazon(node.path("fulfilled_by_amazon").asBoolean(false))
                .sellerPositiveFeedbackRating(
                        seller.path("positive_percent").isNumber()
                                ? seller.path("positive_percent").asDouble() : null)
                .sellerFeedbackCount(
                        seller.path("ratings_count").isNumber()
                                ? seller.path("ratings_count").asInt() : null)
                .countryCode(read.servedCountry())
                .priceEstimated(price.estimated())
                .priceNote(price.note())
                .source(SOURCE)
                .build();
    }

    private OfferListing offer(Read read, Price price, boolean buyBox, JsonNode seller, String note) {
        return OfferListing.builder()
                .marketplace(Marketplace.AMAZON)
                .marketplaceItemId(read.itemId())
                .sellerId(seller == null ? null : text(seller, "id"))
                .sellerName(seller == null ? null : text(seller, "name"))
                .listingPrice(price.amount())
                .landedPrice(price.amount())
                .currency(price.currency())
                .buyBoxWinner(buyBox)
                .countryCode(read.servedCountry())
                .priceEstimated(price.estimated())
                .priceNote(note)
                .source(SOURCE)
                .build();
    }

    /** The parsed page, shared by the three reads above. */
    private record Read(JsonNode root, String itemId, String servedCountry,
                        AmazonMarketplace market, Price price, String note) {
    }

    private Read read(String json, String asin, String requestedCountry, boolean acceptConverted) {
        if (json == null) {
            throw new MarketplaceApiException(
                    "The page reader found no listing for " + asin + " on Amazon " + requestedCountry + ".");
        }
        JsonNode root = parse(json);
        if (!root.path("found").asBoolean(true)) {
            throw new MarketplaceApiException(
                    "The page reader found no listing for " + asin + " on Amazon " + requestedCountry + ".");
        }

        String servedCountry = AmazonScraperClient.countryForDomain(
                root.path("marketplace").asText(null));
        AmazonMarketplace market = servedCountry == null ? null
                : AmazonMarketplace.find(servedCountry).orElse(null);
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

        return new Read(root, itemId, servedCountry, market, price,
                notes.isEmpty() ? null : String.join(" ", notes));
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
