package com.priceintel.backend.marketplace.amazon;

import java.util.Arrays;
import java.util.Optional;

/**
 * The Amazon marketplaces (regions) the SP-API can be queried against.
 *
 * <p>A single ASIN is global, but its <b>price</b> is not: a product listed only
 * on Amazon UK has no competitive price in the US marketplace. Amazon returns
 * the catalog record regardless, which is how an item ends up on screen with a
 * blank price. To fix that we need to retry the same lookup against other
 * marketplaces, and each marketplace has both its own id and its own regional
 * SP-API host — hence this registry.</p>
 *
 * <p>Ordering here is the built-in fallback order (highest catalogue coverage
 * first); override it with {@code amazon.sp-api.search-marketplace-ids}.</p>
 */
public enum AmazonMarketplace {

    // ---- North America ----
    US("ATVPDKIKX0DER", "NA", "US", "USD"),
    CA("A2EUQ1WTGCTBG2", "NA", "CA", "CAD"),
    MX("A1AM78C64UM0Y8", "NA", "MX", "MXN"),
    BR("A2Q3Y263D00KWC", "NA", "BR", "BRL"),

    // ---- Europe ----
    UK("A1F83G8C2ARO7P", "EU", "GB", "GBP"),
    DE("A1PA6795UKMFR9", "EU", "DE", "EUR"),
    FR("A13V1IB3VIYZZH", "EU", "FR", "EUR"),
    IT("APJ6JRA9NG5V4", "EU", "IT", "EUR"),
    ES("A1RKKUPIHCS9HS", "EU", "ES", "EUR"),
    NL("A1805IZSGTT6HS", "EU", "NL", "EUR"),
    SE("A2NODRKZP88ZB9", "EU", "SE", "SEK"),
    PL("A1C3SOZRARQ6R3", "EU", "PL", "PLN"),
    BE("AMEN7PMS3EDWL", "EU", "BE", "EUR"),
    TR("A33AVAJ2PDY3EV", "EU", "TR", "TRY"),
    AE("A2VIGQ35RCS4UG", "EU", "AE", "AED"),
    SA("A17E79C6D8DWNP", "EU", "SA", "SAR"),
    EG("ARBP9OOSHTCHU", "EU", "EG", "EGP"),
    IN("A21TJRUUN4KGV", "EU", "IN", "INR"),
    ZA("AE08WJ6YKNBMC", "EU", "ZA", "ZAR"),

    // ---- Far East ----
    JP("A1VC38T7YXB528", "FE", "JP", "JPY"),
    AU("A39IBJ37TRP1C6", "FE", "AU", "AUD"),
    SG("A19VAU5U5O7RUS", "FE", "SG", "SGD");

    private final String marketplaceId;
    private final String region;
    private final String countryCode;
    private final String currency;

    AmazonMarketplace(String marketplaceId, String region, String countryCode, String currency) {
        this.marketplaceId = marketplaceId;
        this.region = region;
        this.countryCode = countryCode;
        this.currency = currency;
    }

    public String getMarketplaceId() {
        return marketplaceId;
    }

    public String getRegion() {
        return region;
    }

    public String getCountryCode() {
        return countryCode;
    }

    /** The marketplace's local currency — used when a pricing payload omits it. */
    public String getCurrency() {
        return currency;
    }

    /**
     * The shopper-facing storefront host for this marketplace.
     *
     * <p>Distinct from {@link #getEndpoint()}: that is the API we read from,
     * this is the site a person opens. They never coincide — no amount of
     * SP-API host manipulation produces a page a buyer can look at.</p>
     */
    public String getStorefrontHost() {
        return switch (this) {
            case US -> "www.amazon.com";
            case CA -> "www.amazon.ca";
            case MX -> "www.amazon.com.mx";
            case BR -> "www.amazon.com.br";
            case UK -> "www.amazon.co.uk";
            case DE -> "www.amazon.de";
            case FR -> "www.amazon.fr";
            case IT -> "www.amazon.it";
            case ES -> "www.amazon.es";
            case NL -> "www.amazon.nl";
            case SE -> "www.amazon.se";
            case PL -> "www.amazon.pl";
            case BE -> "www.amazon.com.be";
            case TR -> "www.amazon.com.tr";
            case AE -> "www.amazon.ae";
            case SA -> "www.amazon.sa";
            case EG -> "www.amazon.eg";
            case IN -> "www.amazon.in";
            case ZA -> "www.amazon.co.za";
            case JP -> "www.amazon.co.jp";
            case AU -> "www.amazon.com.au";
            case SG -> "www.amazon.sg";
        };
    }

    /**
     * The public product page for an ASIN <em>in this marketplace</em>.
     *
     * <p>The region matters. An ASIN priced in GBP against amazon.co.uk opens on
     * amazon.com as a different offer, or as nothing at all — so a link built
     * from the ASIN alone would contradict the price shown beside it.</p>
     */
    public String productUrl(String asin) {
        return asin == null || asin.isBlank()
                ? null : "https://" + getStorefrontHost() + "/dp/" + asin;
    }

    /** The regional SP-API host this marketplace must be queried through. */
    public String getEndpoint() {
        return switch (region) {
            case "EU" -> "https://sellingpartnerapi-eu.amazon.com";
            case "FE" -> "https://sellingpartnerapi-fe.amazon.com";
            default -> "https://sellingpartnerapi-na.amazon.com";
        };
    }

    /** Looks up a marketplace by its Amazon id, or by country/enum name. */
    public static Optional<AmazonMarketplace> find(String idOrCode) {
        if (idOrCode == null || idOrCode.isBlank()) {
            return Optional.empty();
        }
        String needle = idOrCode.trim();
        return Arrays.stream(values())
                .filter(m -> m.marketplaceId.equalsIgnoreCase(needle)
                        || m.name().equalsIgnoreCase(needle)
                        || m.countryCode.equalsIgnoreCase(needle))
                .findFirst();
    }
}
