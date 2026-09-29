package com.priceintel.backend.marketplace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.priceintel.backend.marketplace.Marketplace;
import com.priceintel.backend.utils.MarketplaceUrlParser;

class MarketplaceUrlParserTest {

    private String asin(String url) {
        return MarketplaceUrlParser.parse(url).map(MarketplaceUrlParser.ParsedUrl::itemId).orElse(null);
    }

    @Test
    @DisplayName("the canonical /dp/ form")
    void canonicalDp() {
        assertThat(asin("https://www.amazon.com/dp/B0BDHWDR12")).isEqualTo("B0BDHWDR12");
        assertThat(asin("https://www.amazon.com/dp/B0BDHWDR12/ref=sr_1_3?keywords=airpods"))
                .isEqualTo("B0BDHWDR12");
    }

    @Test
    @DisplayName("the older and mobile forms people actually paste")
    void olderForms() {
        assertThat(asin("https://www.amazon.com/gp/product/B0BDHWDR12")).isEqualTo("B0BDHWDR12");
        assertThat(asin("https://www.amazon.com/gp/aw/d/B0BDHWDR12")).isEqualTo("B0BDHWDR12");
        assertThat(asin("https://www.amazon.com/Apple-AirPods-Pro/dp/B0BDHWDR12/"))
                .isEqualTo("B0BDHWDR12");
        assertThat(asin("https://www.amazon.com/gp/offer-listing?asin=B0BDHWDR12&condition=new"))
                .isEqualTo("B0BDHWDR12");
    }

    @Test
    @DisplayName("the marketplace country comes from the domain")
    void countryFromDomain() {
        assertThat(MarketplaceUrlParser.parse("https://www.amazon.in/dp/B0BDHWDR12")
                .orElseThrow().countryCode()).isEqualTo("IN");
        assertThat(MarketplaceUrlParser.parse("https://www.amazon.co.uk/dp/B0BDHWDR12")
                .orElseThrow().countryCode()).isEqualTo("GB");
        assertThat(MarketplaceUrlParser.parse("https://www.amazon.com/dp/B0BDHWDR12")
                .orElseThrow().countryCode()).isEqualTo("US");
    }

    @Test
    @DisplayName("an eBay item link")
    void ebayItem() {
        var parsed = MarketplaceUrlParser.parse("https://www.ebay.com/itm/295678123456").orElseThrow();
        assertThat(parsed.marketplace()).isEqualTo(Marketplace.EBAY);
        assertThat(parsed.itemId()).isEqualTo("295678123456");

        assertThat(asin("https://www.ebay.com/itm/Logitech-MX-Master-3S/295678123456?hash=item44"))
                .isEqualTo("295678123456");
    }

    @Test
    @DisplayName("building a storefront link, and parsing it back")
    void buildRoundTrips() {
        assertThat(MarketplaceUrlParser.build("AMAZON", "B0DGHMNQ5Z", "US"))
                .isEqualTo("https://www.amazon.com/dp/B0DGHMNQ5Z");
        assertThat(MarketplaceUrlParser.build("AMAZON", "B0DGHMNQ5Z", "GB"))
                .isEqualTo("https://www.amazon.co.uk/dp/B0DGHMNQ5Z");
        assertThat(MarketplaceUrlParser.build("AMAZON", "B0DGHMNQ5Z", "IN"))
                .isEqualTo("https://www.amazon.in/dp/B0DGHMNQ5Z");
        assertThat(MarketplaceUrlParser.build("EBAY", "295678123456", null))
                .isEqualTo("https://www.ebay.com/itm/295678123456");

        // An unknown or absent region falls back to the default rather than
        // producing no link at all.
        assertThat(MarketplaceUrlParser.build("amazon", "B0DGHMNQ5Z", null))
                .isEqualTo("https://www.amazon.com/dp/B0DGHMNQ5Z");
        assertThat(MarketplaceUrlParser.build("AMAZON", "B0DGHMNQ5Z", "ZZ"))
                .isEqualTo("https://www.amazon.com/dp/B0DGHMNQ5Z");

        var parsed = MarketplaceUrlParser.parse(
                MarketplaceUrlParser.build("AMAZON", "B0DGHMNQ5Z", "IN")).orElseThrow();
        assertThat(parsed.itemId()).isEqualTo("B0DGHMNQ5Z");
        assertThat(parsed.countryCode()).isEqualTo("IN");
    }

    @Test
    @DisplayName("nothing to link to yields no link")
    void buildRefusesUnusableInput() {
        assertThat(MarketplaceUrlParser.build("AMAZON", null, "US")).isNull();
        assertThat(MarketplaceUrlParser.build("AMAZON", "  ", "US")).isNull();
        assertThat(MarketplaceUrlParser.build(null, "B0DGHMNQ5Z", "US")).isNull();
        assertThat(MarketplaceUrlParser.build("WALMART", "12345", "US")).isNull();
    }

    @Test
    @DisplayName("a search or category page yields nothing — there is no single item in it")
    void nonProductPages() {
        assertThat(MarketplaceUrlParser.parse("https://www.amazon.com/s?k=airpods+pro")).isEmpty();
        assertThat(MarketplaceUrlParser.parse("https://www.amazon.com/")).isEmpty();
        assertThat(MarketplaceUrlParser.parse("https://example.com/dp/B0BDHWDR12")).isEmpty();
        assertThat(MarketplaceUrlParser.parse(null)).isEmpty();
        assertThat(MarketplaceUrlParser.parse("  ")).isEmpty();
    }
}
