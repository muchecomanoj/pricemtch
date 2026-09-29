package com.priceintel.backend.marketplace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceintel.backend.marketplace.amazon.AmazonProductNormalizer;

/**
 * Which of Amazon's competitive prices is tracked.
 *
 * <p>Amazon returns one competitive price per condition — New and Used arrive
 * together. Reading whichever came first in the array produced phantom price
 * changes: two calls a minute apart returned $99.00 then $92.18 for an
 * unchanged listing, and each flip was recorded as a 7% move that fired alerts
 * and fed recommendations.</p>
 *
 * <p>The payloads below are the real ones Amazon returned for these ASINs.</p>
 */
class AmazonCompetitivePriceChoiceTest {

    private final AmazonProductNormalizer normalizer = new AmazonProductNormalizer(new ObjectMapper());

    /** New $99.00 (id 1) and Used/Good $92.18 (id 2), as Amazon sent them. */
    private static String airpods(boolean newFirst) {
        String priceNew = """
                {"belongsToRequester":false,"condition":"New","subcondition":"New",
                 "Price":{"LandedPrice":{"CurrencyCode":"USD","Amount":99},
                          "ListingPrice":{"CurrencyCode":"USD","Amount":99},
                          "Shipping":{"CurrencyCode":"USD","Amount":0}},
                 "CompetitivePriceId":"1"}""";
        String priceUsed = """
                {"belongsToRequester":false,"condition":"Used","subcondition":"Good",
                 "Price":{"LandedPrice":{"CurrencyCode":"USD","Amount":92.18},
                          "ListingPrice":{"CurrencyCode":"USD","Amount":92.18},
                          "Shipping":{"CurrencyCode":"USD","Amount":0}},
                 "CompetitivePriceId":"2"}""";
        return """
                {"payload":[{"ASIN":"B0DGHMNQ5Z","status":"Success","Product":{
                  "CompetitivePricing":{"CompetitivePrices":[%s,%s]}}}]}
                """.formatted(newFirst ? priceNew : priceUsed, newFirst ? priceUsed : priceNew);
    }

    @Test
    @DisplayName("the New price is tracked, whichever order Amazon sends them in")
    void orderDoesNotChangeThePrice() {
        assertThat(normalizer.extractPrice(airpods(true))).isEqualByComparingTo("99");
        assertThat(normalizer.extractPrice(airpods(false))).isEqualByComparingTo("99");
    }

    @Test
    @DisplayName("the bulk read used by search makes the same choice")
    void bulkReadAgreesWithSingleRead() {
        assertThat(normalizer.extractPricesByAsin(airpods(true)).get("B0DGHMNQ5Z"))
                .isEqualByComparingTo("99");
        assertThat(normalizer.extractPricesByAsin(airpods(false)).get("B0DGHMNQ5Z"))
                .isEqualByComparingTo("99");
    }

    @Test
    @DisplayName("the currency belongs to the price that was chosen")
    void currencyMatchesTheChosenPrice() {
        String mixed = """
                {"payload":[{"ASIN":"X","Product":{"CompetitivePricing":{"CompetitivePrices":[
                  {"condition":"Used","Price":{"ListingPrice":{"CurrencyCode":"CAD","Amount":50}},
                   "CompetitivePriceId":"2"},
                  {"condition":"New","Price":{"ListingPrice":{"CurrencyCode":"USD","Amount":80}},
                   "CompetitivePriceId":"1"}]}}}]}
                """;

        assertThat(normalizer.extractPrice(mixed)).isEqualByComparingTo("80");
        assertThat(normalizer.extractCurrency(mixed)).isEqualTo("USD");
    }

    /** Amazon's answer for this ASIN minutes later: the New entry simply gone. */
    private static final String USED_ONLY_ENTRY = """
            {"payload":[{"ASIN":"B0DGHMNQ5Z","Product":{"CompetitivePricing":{
              "CompetitivePrices":[
                {"condition":"Used","subcondition":"Good",
                 "Price":{"ListingPrice":{"CurrencyCode":"USD","Amount":92.18}},
                 "CompetitivePriceId":"2"}],
              "NumberOfOfferListings":[{"condition":"New","Count":5},
                                       {"condition":"Used","Count":31}]}}}]}
            """;

    @Test
    @DisplayName("a missing New price is unknown, never the Used price")
    void aMissingNewPriceIsNotTheUsedPrice() {
        // Seen live: the same ASIN returned New $99 and Used $92.18 together one
        // minute, and Used alone the next — while still reporting five New
        // offers. Reading $92.18 in that moment is what made the tracked price
        // jump between two figures for days.
        assertThat(normalizer.extractPrice(USED_ONLY_ENTRY)).isNull();
    }

    @Test
    @DisplayName("Amazon's own offer counts say whether a New market exists")
    void offerCountsAreReadable() {
        assertThat(normalizer.offerCount(USED_ONLY_ENTRY, "New")).isEqualTo(5);
        assertThat(normalizer.offerCount(USED_ONLY_ENTRY, "Used")).isEqualTo(31);
        assertThat(normalizer.offerCount(airpods(true), "New")).isZero();   // none stated
    }

    @Test
    @DisplayName("a genuinely used-only listing still gets a stable price, as a last resort")
    void usedOnlyIsStable() {
        String usedOnly = """
                {"payload":[{"ASIN":"X","Product":{"CompetitivePricing":{"CompetitivePrices":[
                  {"condition":"Used","subcondition":"Acceptable",
                   "Price":{"ListingPrice":{"CurrencyCode":"USD","Amount":40}},
                   "CompetitivePriceId":"3"},
                  {"condition":"Used","subcondition":"VeryGood",
                   "Price":{"ListingPrice":{"CurrencyCode":"USD","Amount":55}},
                   "CompetitivePriceId":"2"}]}}}]}
                """;

        // Not from extractPrice, which speaks only for New.
        assertThat(normalizer.extractPrice(usedOnly)).isNull();
        // Lowest competitive-price id wins — any rule would do, provided it is
        // the same one every time.
        assertThat(normalizer.extractAnyCompetitivePrice(usedOnly)).isEqualByComparingTo("55");
        assertThat(normalizer.extractCurrency(usedOnly)).isEqualTo("USD");
    }

    @Test
    @DisplayName("the bulk read leaves an ASIN unpriced rather than quoting its used price")
    void bulkReadSkipsUsedOnly() {
        assertThat(normalizer.extractPricesByAsin(USED_ONLY_ENTRY)).isEmpty();
    }

    @Test
    @DisplayName("no competitive price at all reads as unknown, not as zero")
    void noPriceIsNull() {
        assertThat(normalizer.extractPrice("""
                {"payload":[{"ASIN":"X","Product":{"CompetitivePricing":{"CompetitivePrices":[]}}}]}
                """)).isNull();
        assertThat(normalizer.extractPrice(null)).isNull();
    }

    @Test
    @DisplayName("the requester's own offer is used only when there is no competitive price")
    void ownOfferIsTheLastResort() {
        String bothPresent = """
                {"payload":[{"ASIN":"X","Product":{
                  "Offers":[{"BuyingPrice":{"ListingPrice":{"CurrencyCode":"USD","Amount":123}}}],
                  "CompetitivePricing":{"CompetitivePrices":[
                    {"condition":"New","Price":{"ListingPrice":{"CurrencyCode":"USD","Amount":80}},
                     "CompetitivePriceId":"1"}]}}}]}
                """;
        String offerOnly = """
                {"payload":[{"ASIN":"X","Product":{
                  "Offers":[{"BuyingPrice":{"ListingPrice":{"CurrencyCode":"USD","Amount":123}}}],
                  "CompetitivePricing":{"CompetitivePrices":[]}}}]}
                """;

        // The market's price, not ours, when the market states one.
        assertThat(normalizer.extractPrice(bothPresent)).isEqualByComparingTo("80");
        assertThat(normalizer.extractPrice(offerOnly)).isEqualByComparingTo("123");
    }
}
