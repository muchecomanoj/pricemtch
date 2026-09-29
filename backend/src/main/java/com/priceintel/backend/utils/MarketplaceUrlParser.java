package com.priceintel.backend.utils;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.priceintel.backend.marketplace.Marketplace;

/**
 * Pulls the item identifier out of a pasted marketplace URL.
 *
 * <p>Server-side because URL shapes are the marketplace's business, not the
 * browser's: Amazon alone uses {@code /dp/}, {@code /gp/product/},
 * {@code /product/} and a bare {@code ?asin=} query, and adds new ones without
 * notice. A copy of these patterns in the client would drift, and each client
 * would drift differently.</p>
 */
public final class MarketplaceUrlParser {

    private MarketplaceUrlParser() {
    }

    /**
     * Where the identifier sits in an Amazon URL. Ordered most specific first —
     * {@code /dp/} is the canonical form, the rest are older or share-link
     * variants that still resolve.
     */
    private static final Pattern[] AMAZON_PATTERNS = {
        Pattern.compile("/dp/([A-Z0-9]{10})(?:[/?]|$)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("/gp/product/([A-Z0-9]{10})(?:[/?]|$)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("/gp/aw/d/([A-Z0-9]{10})(?:[/?]|$)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("/product/([A-Z0-9]{10})(?:[/?]|$)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("[?&]asin=([A-Z0-9]{10})(?:&|$)", Pattern.CASE_INSENSITIVE),
    };

    /** eBay item ids are numeric and 9–15 digits. */
    private static final Pattern[] EBAY_PATTERNS = {
        Pattern.compile("/itm/(?:[^/]+/)?(\\d{9,15})(?:[/?]|$)"),
        Pattern.compile("[?&]item=(\\d{9,15})(?:&|$)"),
    };

    /** The Amazon domain, which tells us the marketplace the link belongs to. */
    private static final Pattern AMAZON_HOST =
            Pattern.compile("amazon\\.([a-z.]{2,6})", Pattern.CASE_INSENSITIVE);

    /** What a pasted URL turned out to be. */
    public record ParsedUrl(Marketplace marketplace, String itemId, String identifierType,
            String countryCode) {
    }

    /**
     * @return the marketplace and item id, or empty when the text is not a
     *         recognised product URL — including when it is a search or category
     *         page, which has no single item to extract
     */
    public static Optional<ParsedUrl> parse(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        String value = url.trim();
        String lower = value.toLowerCase();

        if (lower.contains("amazon.")) {
            for (Pattern p : AMAZON_PATTERNS) {
                Matcher m = p.matcher(value);
                if (m.find()) {
                    return Optional.of(new ParsedUrl(Marketplace.AMAZON,
                            m.group(1).toUpperCase(), "ASIN", countryFromAmazonHost(value)));
                }
            }
            return Optional.empty();
        }
        if (lower.contains("ebay.")) {
            for (Pattern p : EBAY_PATTERNS) {
                Matcher m = p.matcher(value);
                if (m.find()) {
                    return Optional.of(new ParsedUrl(Marketplace.EBAY,
                            m.group(1), "MARKETPLACE_ID", null));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * The inverse of {@link #parse}: the storefront page for an item we hold
     * only as a marketplace and an id.
     *
     * <p>Here for the same reason parsing is: the URL shapes belong to the
     * marketplace. A fallback for rows stored before the connectors began
     * recording a URL — prefer the stored one whenever there is one, since it
     * knows which region the listing was actually priced in and this cannot.</p>
     *
     * @param countryCode the listing's marketplace, or null to assume the
     *                    default region
     * @return the URL, or null when the marketplace or id is unusable
     */
    public static String build(String marketplace, String itemId, String countryCode) {
        if (marketplace == null || itemId == null || itemId.isBlank()) {
            return null;
        }
        String id = itemId.trim();
        return switch (marketplace.trim().toUpperCase()) {
            case "AMAZON" -> com.priceintel.backend.marketplace.amazon.AmazonMarketplace
                    .find(countryCode)
                    .orElse(com.priceintel.backend.marketplace.amazon.AmazonMarketplace.US)
                    .productUrl(id);
            case "EBAY" -> "https://" + ebayHost(countryCode) + "/itm/" + id;
            default -> null;
        };
    }

    /** The eBay site host for a storefront, defaulting to ebay.com. */
    private static String ebayHost(String countryCode) {
        String c = Storefront.normalise(countryCode);
        if (c == null) {
            return "www.ebay.com";
        }
        return switch (c) {
            case "GB" -> "www.ebay.co.uk";
            case "CA" -> "www.ebay.ca";
            case "DE" -> "www.ebay.de";
            case "FR" -> "www.ebay.fr";
            case "IT" -> "www.ebay.it";
            case "ES" -> "www.ebay.es";
            case "AU" -> "www.ebay.com.au";
            case "IN" -> "www.ebay.in";
            default -> "www.ebay.com";
        };
    }

    /**
     * The marketplace's country, so a link pasted from amazon.in is not searched
     * against the US catalogue and reported missing.
     */
    private static String countryFromAmazonHost(String url) {
        Matcher m = AMAZON_HOST.matcher(url);
        if (!m.find()) {
            return null;
        }
        String tld = m.group(1).toLowerCase();
        return switch (tld) {
            case "com" -> "US";
            case "ca" -> "CA";
            case "co.uk" -> "GB";
            case "de" -> "DE";
            case "fr" -> "FR";
            case "it" -> "IT";
            case "es" -> "ES";
            case "in" -> "IN";
            case "co.jp" -> "JP";
            case "com.au" -> "AU";
            case "com.mx" -> "MX";
            case "com.br" -> "BR";
            default -> null;
        };
    }
}
