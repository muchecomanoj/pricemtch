package com.priceintel.backend.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.priceintel.backend.dto.request.Destination;
import com.priceintel.backend.dto.response.EbayOtherSellersResponse;
import com.priceintel.backend.dto.response.EbayOtherSellersResponse.Seller;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.marketplace.ebay.EbayApiClient;
import com.priceintel.backend.marketplace.ebay.EbayApiProperties;
import com.priceintel.backend.marketplace.ebay.EbayResponseNormalizer;
import com.priceintel.backend.marketplace.model.ItemIdentifier;
import com.priceintel.backend.utils.Storefront;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Finds the other eBay sellers of the product one listing is for.
 *
 * <p>Amazon and eBay organise competition differently, and the Amazon offers
 * panel does not fit eBay at all. On Amazon one product page carries many
 * sellers' offers, and one of them wins the Buy Box. On eBay every seller runs
 * a separate listing and there is no winner — the buyer compares. So an eBay
 * listing's competitors are the other listings of the same product, found by
 * the product code they share.</p>
 *
 * <p>What eBay offers in exchange is worth having: sellers are named, their
 * feedback is public, and the lowest fixed price at which a product is actually
 * for sale is a better guide to a price floor than any single retail listing.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EbayOtherSellersService {

    private static final int DEFAULT_LIMIT = 50;

    private final EbayApiClient client;
    private final EbayResponseNormalizer normalizer;
    private final EbayApiProperties props;

    /**
     * @param region      the eBay site the listing belongs to; null for the default site
     * @param destination where the buyer is, so shipping can be quoted. Without
     *                    it many sellers quote none, and their rows cannot count
     *                    toward the cheapest landed price.
     */
    public EbayOtherSellersResponse find(String itemId, String region, Destination destination,
                                         Integer limit) {
        if (itemId == null || itemId.isBlank()) {
            throw new BadRequestException("An eBay item id is required.");
        }
        String site = siteFor(region);
        String storefront = Storefront.normalise(site);

        String sourceJson = client.getItem(itemId.trim(), destination, site);
        Seller source = normalizer.toSellerListing(sourceJson);
        source.setThisListing(true);
        String currency = source.getCurrency();

        // UPC and EAN arrive as GTIN on eBay. A GTIN names one exact product;
        // an EPID names eBay's catalogue entry for it, which is nearly as
        // precise. An MPN alone is not used: different brands reuse the same
        // part numbers, and matching on one would call unrelated products rivals.
        List<ItemIdentifier> codes = normalizer.toListingDetails(sourceJson).getIdentifiers();
        ItemIdentifier code = pick(codes, "GTIN");
        if (code == null) {
            code = pick(codes, "EPID");
        }
        if (code == null) {
            return EbayOtherSellersResponse.builder()
                    .sourceItemId(itemId).storefront(storefront).currency(currency)
                    .listingsFound(0)
                    .sellers(List.of(source))
                    .note("This listing states no UPC, EAN or eBay product ID, so other sellers of "
                            + "the same product cannot be found reliably. Searching by title would "
                            + "return similar products, not this one.")
                    .build();
        }

        int size = limit != null && limit > 0 ? limit : DEFAULT_LIMIT;
        String json = client.searchProductListings(code.getType(), code.getValue(), size,
                destination, site);
        List<Seller> found = normalizer.toSellerListings(json);
        int listingsFound = found.size();

        List<Seller> rows = new ArrayList<>(found.size() + 1);
        boolean sourceSeen = false;
        for (Seller s : found) {
            if (sameListing(s, source)) {
                // The same listing twice. The full item copy is kept: it was
                // fetched directly, so it is the more complete of the two.
                sourceSeen = true;
                continue;
            }
            rows.add(s);
        }
        // Always present, whether or not the search returned it: the listing the
        // user opened is the reference every other row is compared against.
        rows.add(source);

        List<Seller> sellers = onePerSeller(rows);
        markCheapestPerCondition(sellers, currency);
        sellers.sort(ORDER);

        BigDecimal cheapest = sellers.stream()
                .filter(Seller::isCheapestInCondition)
                .filter(s -> Objects.equals(s.getCondition(), source.getCondition()))
                .map(Seller::getLandedPrice)
                .findFirst().orElse(null);
        int unknownShipping = (int) sellers.stream().filter(s -> !s.isShippingKnown()).count();

        log.info("eBay other sellers for {} on {} by {} {}: {} listing(s), {} seller(s), "
                        + "{} without shipping, source seen in search: {}",
                itemId, site, code.getType(), code.getValue(), listingsFound, sellers.size(),
                unknownShipping, sourceSeen);

        return EbayOtherSellersResponse.builder()
                .sourceItemId(itemId)
                .storefront(storefront)
                .currency(currency)
                .matchedBy(code.getType())
                .matchedValue(code.getValue())
                .listingsFound(listingsFound)
                .sellers(sellers)
                .cheapestLandedPrice(cheapest)
                .shippingUnknown(unknownShipping)
                .note(note(sellers.size(), unknownShipping, destination))
                .build();
    }

    // ---------- shaping ----------

    /**
     * Known landed prices first, cheapest first; then rows with unknown
     * shipping by item price. An unknown total never ranks above a known one.
     */
    private static final Comparator<Seller> ORDER = Comparator
            .comparing((Seller s) -> s.getLandedPrice() == null)
            .thenComparing(s -> s.getLandedPrice() != null ? s.getLandedPrice() : s.getPrice(),
                    Comparator.nullsLast(Comparator.naturalOrder()));

    /**
     * One row per seller <em>per condition</em>, keeping their cheapest listing.
     *
     * <p>A seller with three used listings of one product is one used competitor,
     * not three — counting each would make a busy seller look like a crowded
     * market.</p>
     *
     * <p>Per condition, not per seller. Checked against live eBay: one seller
     * listed the same AirPods as Open box at $71, New at $88 and For parts at
     * $51. Collapsing by seller alone kept the $51 parts unit and hid their new
     * listing — the only one that competes with new stock.</p>
     *
     * <p>The source listing is kept as its group's row even when that seller has
     * a cheaper one in the same condition, because it is the listing the user is
     * looking at; the count still shows there are others.</p>
     */
    private List<Seller> onePerSeller(List<Seller> rows) {
        Map<String, List<Seller>> bySeller = new LinkedHashMap<>();
        for (Seller s : rows) {
            String condition = s.getCondition() == null ? "" : s.getCondition().toLowerCase();
            // A listing with no username cannot be grouped with anything.
            String key = s.getSeller() != null
                    ? "seller:" + s.getSeller().toLowerCase() + "|" + condition
                    : "item:" + s.getItemId();
            bySeller.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }
        List<Seller> out = new ArrayList<>(bySeller.size());
        for (List<Seller> group : bySeller.values()) {
            Seller keep = group.stream().filter(Seller::isThisListing).findFirst()
                    .orElseGet(() -> group.stream().min(ORDER).orElseThrow());
            keep.setListingsFromSeller(group.size());
            out.add(keep);
        }
        return out;
    }

    /**
     * Flags the cheapest known landed price within each condition.
     *
     * <p>Only rows in the comparison currency and with quoted shipping take part.
     * A row missing either cannot be ranked honestly.</p>
     */
    private void markCheapestPerCondition(List<Seller> sellers, String currency) {
        Map<String, Seller> cheapest = new HashMap<>();
        for (Seller s : sellers) {
            if (s.getLandedPrice() == null
                    || (currency != null && !currency.equalsIgnoreCase(s.getCurrency()))) {
                continue;
            }
            String condition = s.getCondition() == null ? "" : s.getCondition().toLowerCase();
            Seller best = cheapest.get(condition);
            if (best == null || s.getLandedPrice().compareTo(best.getLandedPrice()) < 0) {
                cheapest.put(condition, s);
            }
        }
        // Ties share the flag: two sellers at the same landed price are jointly
        // cheapest, and choosing one would be arbitrary.
        for (Seller s : sellers) {
            if (s.getLandedPrice() == null) {
                continue;
            }
            Seller best = cheapest.get(s.getCondition() == null ? "" : s.getCondition().toLowerCase());
            s.setCheapestInCondition(best != null
                    && s.getLandedPrice().compareTo(best.getLandedPrice()) == 0
                    && (currency == null || currency.equalsIgnoreCase(s.getCurrency())));
        }
    }

    private String note(int sellers, int unknownShipping, Destination destination) {
        List<String> parts = new ArrayList<>();
        if (sellers <= 1) {
            parts.add("No other fixed-price sellers of this product were found on this eBay site.");
        }
        if (unknownShipping > 0) {
            boolean hasDestination = destination != null && !destination.isEmpty();
            parts.add(unknownShipping + " seller(s) did not quote shipping"
                    + (hasDestination ? " to this destination" : "")
                    + ", so their total price is unknown and they are not ranked as cheapest."
                    + (hasDestination ? "" : " Adding a delivery country usually fixes this."));
        }
        return parts.isEmpty() ? null : String.join(" ", parts);
    }

    // ---------- helpers ----------

    private String siteFor(String region) {
        if (region == null || region.isBlank()) {
            return props.getMarketplaceId();
        }
        String site = EbayApiClient.marketplaceIdFor(region);
        if (site == null) {
            throw new BadRequestException("eBay has no marketplace for region " + region
                    + ". Use a two-letter country code such as US, GB or CA.");
        }
        return site;
    }

    private ItemIdentifier pick(List<ItemIdentifier> codes, String type) {
        if (codes == null) {
            return null;
        }
        return codes.stream()
                .filter(c -> type.equalsIgnoreCase(c.getType()))
                .filter(c -> usable(type, c.getValue()))
                .findFirst().orElse(null);
    }

    /**
     * Whether a stated code can actually be searched.
     *
     * <p>eBay sellers fill the UPC field by hand, and "Does not apply" is one of
     * the commonest things they type — it is stored in this very database as a
     * GTIN. Searched for, it would return whatever eBay matches that text
     * against. A real GTIN is 8, 12, 13 or 14 digits.</p>
     */
    private static boolean usable(String type, String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String v = value.trim();
        if ("GTIN".equalsIgnoreCase(type)) {
            return v.matches("\\d{8}|\\d{12,14}");
        }
        return v.matches("\\d+");   // an EPID is numeric
    }

    /**
     * Whether two rows are the same eBay listing.
     *
     * <p>By legacy id where both have one: the same listing is {@code 167815729577}
     * to a person and {@code v1|167815729577|0} to the API.</p>
     */
    private boolean sameListing(Seller a, Seller b) {
        if (a.getLegacyItemId() != null && b.getLegacyItemId() != null) {
            return a.getLegacyItemId().equals(b.getLegacyItemId());
        }
        return a.getItemId() != null && a.getItemId().equals(b.getItemId());
    }
}
