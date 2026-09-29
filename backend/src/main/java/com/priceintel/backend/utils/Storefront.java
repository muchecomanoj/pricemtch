package com.priceintel.backend.utils;

import java.net.URI;
import java.util.Locale;

import com.priceintel.backend.marketplace.amazon.AmazonMarketplace;

/**
 * Which country's storefront a marketplace listing belongs to.
 *
 * <p>A marketplace name does not identify a catalogue. amazon.com and amazon.ca
 * are separate shops with separate prices, currencies and sellers, and the same
 * ASIN can exist in both. Treating "AMAZON + ASIN" as one listing merged their
 * price histories into a single series that alternated between dollars and
 * Canadian dollars, and recorded every alternation as a price change. The
 * storefront is therefore part of a listing's identity everywhere it is stored.</p>
 *
 * <p>Always an ISO 3166 two-letter country code: {@code US}, {@code CA},
 * {@code GB}. Amazon's own enum names the UK marketplace {@code UK}; that is
 * normalised to {@code GB} so one country never has two spellings in the
 * database.</p>
 */
public final class Storefront {

    /**
     * Where a listing is assumed to live when nothing about it says otherwise.
     *
     * <p>Only reached for rows with no region, no URL and no currency — which,
     * in practice, are rows that were never priced. It is the storefront every
     * region-less lookup has always tried first.</p>
     */
    public static final String DEFAULT = "US";

    private Storefront() {
    }

    /**
     * The canonical code for anything that names a country or marketplace, or
     * null when it names neither.
     *
     * <p>Accepts {@code CA}, {@code ca}, {@code UK}, an Amazon marketplace id
     * such as {@code A2EUQ1WTGCTBG2}, or an eBay site id such as
     * {@code EBAY_GB}.</p>
     */
    public static String normalise(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String c = code.trim().toUpperCase(Locale.ROOT);
        if (c.startsWith("EBAY_")) {
            c = c.substring(5);
        }
        if (c.equals("UK")) {
            return "GB";
        }
        var amazon = AmazonMarketplace.find(c);
        if (amazon.isPresent()) {
            return amazon.get().getCountryCode();
        }
        return c.matches("[A-Z]{2}") ? c : null;
    }

    /** The storefront a listing URL points at, or null when it cannot be told. */
    public static String fromUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String host;
        try {
            host = URI.create(url.trim()).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (host == null) {
            return null;
        }
        host = host.toLowerCase(Locale.ROOT);
        if (host.startsWith("www.")) {
            host = host.substring(4);
        }
        if (host.contains("amazon.")) {
            for (AmazonMarketplace m : AmazonMarketplace.values()) {
                String storefront = m.getStorefrontHost().toLowerCase(Locale.ROOT);
                if (storefront.startsWith("www.")) {
                    storefront = storefront.substring(4);
                }
                if (host.equals(storefront) || host.endsWith("." + storefront)) {
                    return m.getCountryCode();
                }
            }
            return null;
        }
        if (host.contains("ebay.")) {
            // Longest suffixes first, so ebay.com.au is not read as ebay.com.
            if (host.endsWith("ebay.com.au")) return "AU";
            if (host.endsWith("ebay.co.uk")) return "GB";
            if (host.endsWith("ebay.ca")) return "CA";
            if (host.endsWith("ebay.de")) return "DE";
            if (host.endsWith("ebay.fr")) return "FR";
            if (host.endsWith("ebay.it")) return "IT";
            if (host.endsWith("ebay.es")) return "ES";
            if (host.endsWith("ebay.in")) return "IN";
            if (host.endsWith("ebay.com")) return "US";
        }
        return null;
    }

    /**
     * The storefront a currency implies, or null when it implies more than one.
     *
     * <p>EUR is deliberately null: it is the currency of half a dozen Amazon and
     * eBay storefronts, and guessing one would file a French listing under
     * Germany.</p>
     */
    public static String fromCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return null;
        }
        return switch (currency.trim().toUpperCase(Locale.ROOT)) {
            case "USD" -> "US";
            case "CAD" -> "CA";
            case "GBP" -> "GB";
            case "MXN" -> "MX";
            case "BRL" -> "BR";
            case "SEK" -> "SE";
            case "PLN" -> "PL";
            case "TRY" -> "TR";
            case "AED" -> "AE";
            case "SAR" -> "SA";
            case "EGP" -> "EG";
            case "INR" -> "IN";
            case "ZAR" -> "ZA";
            case "JPY" -> "JP";
            case "AUD" -> "AU";
            case "SGD" -> "SG";
            default -> null;
        };
    }

    /**
     * The storefront to record for a listing, from the most to the least
     * reliable evidence.
     *
     * <ol>
     *   <li>What the marketplace call itself reported, or the region explicitly
     *       searched — a statement of where the data came from.</li>
     *   <li>The listing's URL — the page it actually lives on.</li>
     *   <li>The price's currency — unambiguous for every currency but EUR.</li>
     *   <li>{@link #DEFAULT}.</li>
     * </ol>
     */
    public static String resolve(String reported, String url, String currency) {
        String s = normalise(reported);
        if (s != null) {
            return s;
        }
        s = fromUrl(url);
        if (s != null) {
            return s;
        }
        s = fromCurrency(currency);
        return s != null ? s : DEFAULT;
    }
}
