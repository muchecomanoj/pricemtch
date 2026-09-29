package com.priceintel.backend.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceintel.backend.dto.request.Destination;
import com.priceintel.backend.dto.response.EbayOtherSellersResponse;
import com.priceintel.backend.dto.response.EbayOtherSellersResponse.Seller;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.marketplace.ebay.EbayApiClient;
import com.priceintel.backend.marketplace.ebay.EbayApiProperties;
import com.priceintel.backend.marketplace.ebay.EbayResponseNormalizer;
import com.priceintel.backend.service.impl.EbayOtherSellersService;

/**
 * Other eBay sellers of the same product.
 *
 * <p>eBay has no Buy Box: every seller runs a separate listing, so a listing's
 * competitors are the other listings sharing its product code. These cases pin
 * the rules that make that comparison honest.</p>
 */
class EbayOtherSellersTest {

    private final EbayApiClient client = mock(EbayApiClient.class);
    private final EbayOtherSellersService service = new EbayOtherSellersService(
            client, new EbayResponseNormalizer(new ObjectMapper()), new EbayApiProperties());

    private static final String SOURCE_ID = "v1|111111111111|0";

    /** The listing being compared: New, $60 + $5 shipping, UPC 0194252707463. */
    private static final String SOURCE_ITEM = """
            {"itemId":"v1|111111111111|0","legacyItemId":"111111111111",
             "title":"Apple AirPods 4","itemWebUrl":"https://www.ebay.com/itm/111111111111",
             "condition":"New","gtin":"0194252707463","epid":"12345",
             "price":{"value":"60.00","currency":"USD"},
             "shippingOptions":[{"shippingCost":{"value":"5.00","currency":"USD"}}],
             "seller":{"username":"mystore","feedbackPercentage":"99.1","feedbackScore":820}}
            """;

    private void sourceIs(String json) {
        when(client.getItem(eq(SOURCE_ID), any(), anyString())).thenReturn(json);
    }

    private void searchReturns(String... summaries) {
        when(client.searchProductListings(anyString(), anyString(), anyInt(), any(), anyString()))
                .thenReturn("{\"itemSummaries\":[" + String.join(",", summaries) + "]}");
    }

    private static String summary(String legacyId, String seller, String feedbackPct, int score,
                                  String condition, String price, String shipping) {
        String ship = shipping == null ? ""
                : ",\"shippingOptions\":[{\"shippingCost\":{\"value\":\"" + shipping + "\",\"currency\":\"USD\"}}]";
        return "{\"itemId\":\"v1|" + legacyId + "|0\",\"legacyItemId\":\"" + legacyId + "\","
                + "\"title\":\"AirPods 4\",\"condition\":\"" + condition + "\","
                + "\"price\":{\"value\":\"" + price + "\",\"currency\":\"USD\"}" + ship + ","
                + "\"seller\":{\"username\":\"" + seller + "\",\"feedbackPercentage\":\"" + feedbackPct
                + "\",\"feedbackScore\":" + score + "}}";
    }

    private Seller row(EbayOtherSellersResponse r, String seller) {
        return r.getSellers().stream().filter(s -> seller.equals(s.getSeller())).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("finds other sellers by the listing's UPC, with feedback and landed price")
    void findsBySharedGtin() {
        sourceIs(SOURCE_ITEM);
        searchReturns(summary("222222222222", "bigshop", "99.8", 15400, "New", "55.00", "0.00"));

        EbayOtherSellersResponse r = service.find(SOURCE_ID, null, null, null);

        assertThat(r.getMatchedBy()).isEqualTo("GTIN");
        assertThat(r.getMatchedValue()).isEqualTo("0194252707463");
        verify(client).searchProductListings(eq("GTIN"), eq("0194252707463"), anyInt(), any(), eq("EBAY_US"));

        Seller big = row(r, "bigshop");
        assertThat(big.getFeedbackPercent()).isEqualTo(99.8);   // eBay sends it as a string
        assertThat(big.getFeedbackScore()).isEqualTo(15400);
        assertThat(big.getLandedPrice()).isEqualByComparingTo("55.00");
        assertThat(big.isShippingKnown()).isTrue();
    }

    @Test
    @DisplayName("the listing being compared always appears, marked as this listing")
    void sourceAlwaysIncluded() {
        sourceIs(SOURCE_ITEM);
        searchReturns(summary("222222222222", "bigshop", "99.8", 15400, "New", "55.00", "0.00"));

        EbayOtherSellersResponse r = service.find(SOURCE_ID, null, null, null);

        Seller mine = row(r, "mystore");
        assertThat(mine.isThisListing()).isTrue();
        assertThat(mine.getLandedPrice()).isEqualByComparingTo("65.00");
    }

    @Test
    @DisplayName("the source returned by the search too is not listed twice")
    void sourceNotDuplicated() {
        sourceIs(SOURCE_ITEM);
        // Same listing, in the search's shape.
        searchReturns(summary("111111111111", "mystore", "99.1", 820, "New", "60.00", "5.00"),
                summary("222222222222", "bigshop", "99.8", 15400, "New", "55.00", "0.00"));

        EbayOtherSellersResponse r = service.find(SOURCE_ID, null, null, null);

        assertThat(r.getSellers()).hasSize(2);
        assertThat(r.getSellers()).filteredOn(Seller::isThisListing).hasSize(1);
    }

    @Test
    @DisplayName("sorted by landed price and the cheapest is marked")
    void cheapestMarked() {
        sourceIs(SOURCE_ITEM);
        searchReturns(summary("222222222222", "bigshop", "99.8", 15400, "New", "55.00", "0.00"),
                summary("333333333333", "smallshop", "97.0", 40, "New", "58.00", "9.00"));

        EbayOtherSellersResponse r = service.find(SOURCE_ID, null, null, null);

        assertThat(r.getSellers()).extracting(Seller::getSeller)
                .containsExactly("bigshop", "mystore", "smallshop");   // 55, 65, 67
        assertThat(row(r, "bigshop").isCheapestInCondition()).isTrue();
        assertThat(row(r, "mystore").isCheapestInCondition()).isFalse();
        assertThat(r.getCheapestLandedPrice()).isEqualByComparingTo("55.00");
    }

    @Test
    @DisplayName("cheapest is per condition — a used unit does not undercut new stock")
    void cheapestPerCondition() {
        sourceIs(SOURCE_ITEM);
        searchReturns(summary("222222222222", "usedguy", "98.0", 300, "Used", "40.00", "0.00"),
                summary("333333333333", "bigshop", "99.8", 15400, "New", "62.00", "0.00"));

        EbayOtherSellersResponse r = service.find(SOURCE_ID, null, null, null);

        assertThat(row(r, "usedguy").isCheapestInCondition()).isTrue();   // cheapest used
        assertThat(row(r, "bigshop").isCheapestInCondition()).isTrue();   // cheapest new
        // The source is New; its reference is the cheapest *new* price, not $40.
        assertThat(r.getCheapestLandedPrice()).isEqualByComparingTo("62.00");
    }

    @Test
    @DisplayName("a seller who quoted no shipping is shown but never ranked cheapest")
    void unknownShippingNotRanked() {
        sourceIs(SOURCE_ITEM);
        // $30 looks cheapest, but without shipping the total is unknown.
        searchReturns(summary("222222222222", "noship", "99.0", 500, "New", "30.00", null));

        EbayOtherSellersResponse r = service.find(SOURCE_ID, null, null, null);

        Seller noship = row(r, "noship");
        assertThat(noship.getLandedPrice()).isNull();
        assertThat(noship.isShippingKnown()).isFalse();
        assertThat(noship.isCheapestInCondition()).isFalse();
        assertThat(row(r, "mystore").isCheapestInCondition()).isTrue();
        assertThat(r.getShippingUnknown()).isEqualTo(1);
        // Unknown totals sort after every known one.
        assertThat(r.getSellers().get(r.getSellers().size() - 1).getSeller()).isEqualTo("noship");
        assertThat(r.getNote()).contains("did not quote shipping").contains("delivery country");
    }

    @Test
    @DisplayName("one row per seller, showing their cheapest and how many they have")
    void onePerSeller() {
        sourceIs(SOURCE_ITEM);
        searchReturns(summary("222222222222", "bigshop", "99.8", 15400, "New", "59.00", "0.00"),
                summary("333333333333", "bigshop", "99.8", 15400, "New", "54.00", "0.00"),
                summary("444444444444", "BigShop", "99.8", 15400, "New", "57.00", "0.00"));

        EbayOtherSellersResponse r = service.find(SOURCE_ID, null, null, null);

        assertThat(r.getListingsFound()).isEqualTo(3);
        assertThat(r.getSellers()).hasSize(2);   // bigshop + mystore
        Seller big = r.getSellers().stream().filter(s -> !s.isThisListing()).findFirst().orElseThrow();
        assertThat(big.getLandedPrice()).isEqualByComparingTo("54.00");
        assertThat(big.getListingsFromSeller()).isEqualTo(3);
    }

    // ---------- found against live eBay ----------

    @Test
    @DisplayName("a seller in several conditions gets a row per condition — their new listing is not hidden")
    void sellerInSeveralConditions() {
        // Live eBay, UPC 021931129943: vipoutlet listed Open box $71, New $88,
        // For parts $51. One row per seller kept the $51 parts unit and hid the
        // $88 new listing — the only one competing with new stock.
        sourceIs(SOURCE_ITEM);
        searchReturns(summary("136452793362", "vipoutlet", "97.2", 968688, "Open box", "71.00", "0.00"),
                summary("126717779354", "vipoutlet", "97.2", 968688, "New", "88.00", "0.00"),
                summary("136561921520", "vipoutlet", "97.2", 968688, "For parts or not working", "51.00", "0.00"));

        EbayOtherSellersResponse r = service.find(SOURCE_ID, null, null, null);

        assertThat(r.getSellers()).filteredOn(s -> "vipoutlet".equals(s.getSeller()))
                .extracting(Seller::getCondition)
                .containsExactlyInAnyOrder("Open box", "New", "For parts or not working");
    }

    @Test
    @DisplayName("a seller with no feedback yet is unrated, not 0% positive")
    void noFeedbackIsNotZeroPercent() {
        // Live eBay: armony-797, feedbackPercentage "0.0", feedbackScore 0.
        sourceIs(SOURCE_ITEM);
        searchReturns(summary("800622671458", "armony-797", "0.0", 0, "Open box", "64.65", "0.00"));

        Seller newbie = row(service.find(SOURCE_ID, null, null, null), "armony-797");

        assertThat(newbie.getFeedbackScore()).isZero();
        assertThat(newbie.getFeedbackPercent()).isNull();
    }

    @Test
    @DisplayName("\"Does not apply\" typed into the UPC field is not searched for")
    void doesNotApplyIsNotABarcode() {
        // Stored in this database as a GTIN, from real eBay listings.
        sourceIs(SOURCE_ITEM.replace("0194252707463", "Does not apply"));
        searchReturns();

        EbayOtherSellersResponse r = service.find(SOURCE_ID, null, null, null);

        // Falls through to the listing's EPID instead.
        assertThat(r.getMatchedBy()).isEqualTo("EPID");
        verify(client, never()).searchProductListings(eq("GTIN"), anyString(), anyInt(), any(), anyString());
    }

    @Test
    @DisplayName("falls back to eBay's product id when the listing has no barcode")
    void fallsBackToEpid() {
        sourceIs(SOURCE_ITEM.replace("\"gtin\":\"0194252707463\",", ""));
        searchReturns();

        EbayOtherSellersResponse r = service.find(SOURCE_ID, null, null, null);

        assertThat(r.getMatchedBy()).isEqualTo("EPID");
        verify(client).searchProductListings(eq("EPID"), eq("12345"), anyInt(), any(), anyString());
    }

    @Test
    @DisplayName("with no barcode and no product id it does not guess from the title")
    void noIdentifierNoSearch() {
        sourceIs(SOURCE_ITEM.replace("\"gtin\":\"0194252707463\",", "").replace("\"epid\":\"12345\",", ""));

        EbayOtherSellersResponse r = service.find(SOURCE_ID, null, null, null);

        verify(client, never()).searchProductListings(anyString(), anyString(), anyInt(), any(), anyString());
        assertThat(r.getMatchedBy()).isNull();
        assertThat(r.getSellers()).singleElement().extracting(Seller::isThisListing).isEqualTo(true);
        assertThat(r.getNote()).contains("no UPC, EAN");
    }

    @Test
    @DisplayName("the eBay site follows the region, and delivery goes to the destination")
    void regionAndDestination() {
        when(client.getItem(eq(SOURCE_ID), any(), eq("EBAY_GB"))).thenReturn(
                SOURCE_ITEM.replace("USD", "GBP"));
        searchReturns();
        Destination dest = Destination.builder().country("GB").postalCode("SW1A 1AA").build();

        EbayOtherSellersResponse r = service.find(SOURCE_ID, "UK", dest, null);

        assertThat(r.getStorefront()).isEqualTo("GB");
        assertThat(r.getCurrency()).isEqualTo("GBP");
        verify(client).searchProductListings(anyString(), anyString(), anyInt(), eq(dest), eq("EBAY_GB"));
    }

    @Test
    @DisplayName("a region eBay has no site for is refused")
    void unknownRegionRefused() {
        assertThatThrownBy(() -> service.find(SOURCE_ID, "JP", null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("JP");
    }
}
