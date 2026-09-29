package com.priceintel.backend.marketplace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.priceintel.backend.utils.ListingUrl;

/**
 * Keeping a marketplace link storable.
 *
 * <p>The link below is the real one that broke six searches on 28 Sep: eBay
 * returned 598 characters, 560 of them tracking, for an item whose link is 38
 * characters long. The column held 600, the insert failed, and the failed
 * statement took the whole search down with it.</p>
 */
class ListingUrlTest {

    private static final String EBAY_ITEM = "https://www.ebay.com/itm/298606408365";

    private static String withTracking(int trackingChars) {
        return EBAY_ITEM + "?_trkparms=" + "x".repeat(trackingChars);
    }

    @Test
    @DisplayName("a link that fits is stored exactly as the marketplace gave it")
    void shortLinksAreUntouched() {
        assertThat(ListingUrl.fit(EBAY_ITEM)).isEqualTo(EBAY_ITEM);
        String withQuery = EBAY_ITEM + "?var=123456";
        // Some marketplaces put the item id in the query string, so a link that
        // fits is never trimmed.
        assertThat(ListingUrl.fit(withQuery)).isEqualTo(withQuery);
    }

    @Test
    @DisplayName("tracking parameters are dropped only when the link will not fit")
    void trackingIsDroppedWhenTooLong() {
        String tracked = withTracking(1200);
        assertThat(tracked.length()).isGreaterThan(ListingUrl.MAX_LENGTH);

        assertThat(ListingUrl.fit(tracked)).isEqualTo(EBAY_ITEM);
    }

    @Test
    @DisplayName("the old 600-character limit would have kept this listing, not failed it")
    void theRealFailureCase() {
        String tracked = withTracking(550);   // 598 characters in total, the real case

        assertThat(ListingUrl.fit(tracked, 600)).isEqualTo(tracked);       // fits
        assertThat(ListingUrl.fit(withTracking(700), 600)).isEqualTo(EBAY_ITEM);   // trimmed, not rejected
    }

    @Test
    @DisplayName("a link too long even without its query is dropped rather than truncated")
    void unusableLinkIsDropped() {
        // Half a link leads nowhere; no link at least says so.
        String absurd = "https://www.example.com/" + "a".repeat(1200);
        assertThat(ListingUrl.fit(absurd)).isNull();
    }

    @Test
    @DisplayName("nothing in, nothing out")
    void blanks() {
        assertThat(ListingUrl.fit(null)).isNull();
        assertThat(ListingUrl.fit("   ")).isNull();
        assertThat(ListingUrl.fit("  " + EBAY_ITEM + " ")).isEqualTo(EBAY_ITEM);
    }
}
