package com.priceintel.backend.utils;

/**
 * Keeps a marketplace link short enough to store.
 *
 * <p>eBay returns links carrying its own tracking parameters: one seen here was
 * 598 characters, of which 560 were tracking and 38 were the actual link,
 * {@code https://www.ebay.com/itm/298606408365}. Anything past the column's
 * limit failed the insert, and because the failed statement poisons the
 * transaction, one long link cost the user the whole search.</p>
 *
 * <p>The query string is dropped only when the link is too long to store.
 * Short links are left exactly as the marketplace gave them, because some
 * marketplaces do put the item id in the query string.</p>
 */
public final class ListingUrl {

    private ListingUrl() {
    }

    /** The column width for listing links — {@code competitor_listings.url}. */
    public static final int MAX_LENGTH = 1000;

    public static String fit(String url) {
        return fit(url, MAX_LENGTH);
    }

    /**
     * @return the link, the link without its tracking parameters when the full
     *         one will not fit, or null when even that is too long — a
     *         truncated link is a broken link, and a listing without a link is
     *         better than a listing that leads nowhere.
     */
    public static String fit(String url, int maxLength) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        int query = trimmed.indexOf('?');
        if (query > 0) {
            String base = trimmed.substring(0, query);
            if (base.length() <= maxLength) {
                return base;
            }
        }
        return null;
    }
}
