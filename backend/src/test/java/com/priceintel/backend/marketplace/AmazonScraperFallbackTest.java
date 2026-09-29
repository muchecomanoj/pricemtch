package com.priceintel.backend.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceintel.backend.dto.request.MarketplaceSearchRequest;
import com.priceintel.backend.exception.MarketplaceApiException;
import com.priceintel.backend.marketplace.model.ItemIdentifier;
import com.priceintel.backend.marketplace.model.SearchResult;
import com.priceintel.backend.marketplace.scraper.AmazonScraperClient;
import com.priceintel.backend.marketplace.scraper.AmazonScraperNormalizer;
import com.priceintel.backend.marketplace.scraper.AmazonScraperProperties;
import com.priceintel.backend.marketplace.scraper.AmazonScraperSearch;

/**
 * ASIN lookups in marketplaces SP-API is not authorised for.
 *
 * <p>The payloads are the ones the scraper actually returned today, including
 * the awkward ones: a UK page priced in rupees, and a request for the UK
 * answered by Germany.</p>
 */
class AmazonScraperFallbackTest {

    private final AmazonScraperClient client = mock(AmazonScraperClient.class);
    private final AmazonScraperProperties props = new AmazonScraperProperties();
    private final AmazonScraperSearch search = new AmazonScraperSearch(
            props, client, new AmazonScraperNormalizer(new ObjectMapper()));

    AmazonScraperFallbackTest() {
        props.setEnabled(true);
        props.setBaseUrl("http://192.168.10.189:5000");
    }

    private MarketplaceSearchRequest asinIn(String region) {
        return MarketplaceSearchRequest.builder()
                .query("B08RC6981L").identifierType("ASIN").region(region).build();
    }

    /** Real response: amazon.co.uk quoted INR, converted to GBP. */
    private static final String CONVERTED_UK = """
            {"asin":"B08RC6981L","found":true,"marketplace":"co.uk",
             "product_name":"INFURTURE H1 Active Noise Cancelling Headphones",
             "details":{"UPC":"788404793994","Model Number":"BN601A","Colour":"rose gold"},
             "price":{"amount":29.94,"currency":"GBP","converted":true,"raw":"£29.94",
                      "original":{"amount":3837.7,"currency":"INR","raw":"INR 3,837.70"},
                      "fx":{"rate":0.007802,"source":"open.er-api.com"}},
             "price_status":"converted"}
            """;

    /** Real response: the India storefront quoting its own currency. */
    private static final String NATIVE_IN = """
            {"asin":"B0DDHM6D3L","found":true,"marketplace":"in",
             "product_name":"Portronics Conch Theta C in Ear Wired Earphones",
             "details":{"UPC":"8908010947287"},
             "price":{"amount":311.0,"currency":"INR","converted":false,"raw":"₹311.00"},
             "price_status":"ok"}
            """;

    @Nested
    @DisplayName("when the scraper is used at all")
    class WhenUsed {

        @Test
        @DisplayName("an ASIN in an unauthorised marketplace goes to the scraper")
        void unauthorisedRegionUsesScraper() {
            assertThat(search.handles(asinIn("GB"))).isTrue();
            assertThat(search.handles(asinIn("DE"))).isTrue();
            assertThat(search.handles(asinIn("IN"))).isTrue();
        }

        @Test
        @DisplayName("the US and Canada stay with the official API")
        void authorisedRegionsStayWithSpApi() {
            // SP-API holds these, and it is better where it works: no browser,
            // a Buy Box price rather than a page reading.
            assertThat(search.handles(asinIn("US"))).isFalse();
            assertThat(search.handles(asinIn("CA"))).isFalse();
        }

        @Test
        @DisplayName("a search with no region stays with the official API")
        void noRegionStaysWithSpApi() {
            assertThat(search.handles(asinIn(null))).isFalse();
            assertThat(search.handles(asinIn(""))).isFalse();
        }

        @Test
        @DisplayName("only ASIN lookups — the scraper takes nothing else")
        void onlyAsinLookups() {
            assertThat(search.handles(MarketplaceSearchRequest.builder()
                    .query("0194252707463").identifierType("UPC").region("GB").build())).isFalse();
            assertThat(search.handles(MarketplaceSearchRequest.builder()
                    .query("AirPods 4").region("GB").build())).isFalse();
        }

        @Test
        @DisplayName("a marketplace the scraper does not serve is not attempted")
        void unsupportedStorefront() {
            // France and Japan are neither authorised for SP-API nor offered by
            // the scraper. Saying nothing beats answering from another country.
            assertThat(search.handles(asinIn("FR"))).isFalse();
            assertThat(search.handles(asinIn("JP"))).isFalse();
        }

        @Test
        @DisplayName("switched off, nothing reaches it")
        void disabled() {
            props.setEnabled(false);
            assertThat(search.handles(asinIn("GB"))).isFalse();
            props.setEnabled(true);
        }
    }

    @Nested
    @DisplayName("reading the result")
    class Reading {

        @Test
        @DisplayName("a converted price is left out, and the reason is given")
        void convertedPriceIsNotUsed() {
            when(client.getProduct(anyString(), eq("co.uk"))).thenReturn(CONVERTED_UK);

            SearchResult result = search.search(asinIn("GB"));

            var item = result.getItems().get(0);
            assertThat(item.getTitle()).contains("INFURTURE");
            // £29.94 is an import quote carrying duty and shipping, not the
            // price a British buyer pays.
            assertThat(item.getPrice()).isNull();
            assertThat(item.isPriceEstimated()).isFalse();
            assertThat(result.getNote()).contains("import duty").contains("left out");
        }

        @Test
        @DisplayName("a converted price is used when asked for, and carries its own label")
        void convertedPriceCanBeAccepted() {
            props.setAcceptConvertedPrices(true);
            when(client.getProduct(anyString(), eq("co.uk"))).thenReturn(CONVERTED_UK);

            var item = search.search(asinIn("GB")).getItems().get(0);

            assertThat(item.getPrice()).isEqualByComparingTo("29.94");
            assertThat(item.getCurrency()).isEqualTo("GBP");
            // The label travels with the number. Without it, £29.94 is
            // indistinguishable from a price a British buyer actually pays.
            assertThat(item.isPriceEstimated()).isTrue();
            assertThat(item.getPriceNote()).startsWith("Estimate.")
                    .contains("INR").contains("import duty");
            props.setAcceptConvertedPrices(false);
        }

        @Test
        @DisplayName("a natively quoted price is not labelled an estimate")
        void nativePriceIsNotAnEstimate() {
            when(client.getProduct(anyString(), eq("in"))).thenReturn(NATIVE_IN);

            var item = search.search(asinIn("IN")).getItems().get(0);

            assertThat(item.isPriceEstimated()).isFalse();
            assertThat(item.getPriceNote()).isNull();
        }

        @Test
        @DisplayName("a natively quoted price is used as it stands")
        void nativePriceIsUsed() {
            when(client.getProduct(anyString(), eq("in"))).thenReturn(NATIVE_IN);

            SearchResult result = search.search(asinIn("IN"));

            var item = result.getItems().get(0);
            assertThat(item.getPrice()).isEqualByComparingTo("311.0");
            assertThat(item.getCurrency()).isEqualTo("INR");
            assertThat(item.getStorefront()).isEqualTo("IN");
            assertThat(result.getNote()).isNull();
        }

        @Test
        @DisplayName("the storefront recorded is the one that answered, not the one asked for")
        void storefrontIsWhatAnswered() {
            // Seen live: a request for the UK came back from Germany, because
            // the service treats the domain as a starting point.
            when(client.getProduct(anyString(), eq("co.uk"))).thenReturn("""
                    {"asin":"B0DGHMNQ5Z","found":true,"marketplace":"de",
                     "product_name":"Apple AirPods 4","price_status":"unavailable"}
                    """);

            SearchResult result = search.search(asinIn("GB"));

            assertThat(result.getItems().get(0).getStorefront()).isEqualTo("DE");
            assertThat(result.getNote()).contains("Asked for Amazon GB").contains("Amazon DE");
        }

        @Test
        @DisplayName("barcodes from the detail table are kept, and the link opens the right store")
        void identifiersAndLink() {
            props.setAcceptConvertedPrices(true);
            when(client.getProduct(anyString(), eq("co.uk"))).thenReturn(CONVERTED_UK);

            var item = search.search(asinIn("GB")).getItems().get(0);

            assertThat(item.getIdentifiers()).extracting(ItemIdentifier::getType)
                    .contains("ASIN", "UPC", "MPN");
            assertThat(item.getIdentifiers()).extracting(ItemIdentifier::getValue)
                    .contains("788404793994");
            assertThat(item.getUrl()).contains("amazon.co.uk");
            props.setAcceptConvertedPrices(false);
        }

        @Test
        @DisplayName("a parent or variant listing is recorded as the listing Amazon served")
        void variantListingIsRecorded() {
            when(client.getProduct(anyString(), eq("co.uk"))).thenReturn("""
                    {"asin":"B0016URDD0","listing_asin":"B00L707YW4","found":true,
                     "marketplace":"co.uk","product_name":"Barrettine Danish Oil",
                     "price_status":"unavailable"}
                    """);

            SearchResult result = search.search(asinIn("GB"));

            assertThat(result.getItems().get(0).getMarketplaceItemId()).isEqualTo("B00L707YW4");
            assertThat(result.getNote()).contains("parent or variant");
        }

        @Test
        @DisplayName("an ASIN on none of its marketplaces is no results, not an error")
        void notFound() {
            when(client.getProduct(anyString(), anyString())).thenReturn(null);

            SearchResult result = search.search(asinIn("GB"));

            assertThat(result.getItems()).isEmpty();
            assertThat(result.getNote()).contains("none of its marketplaces");
        }

        @Test
        @DisplayName("a scraper failure empties this channel rather than failing the search")
        void failureIsContained() {
            when(client.getProduct(anyString(), anyString()))
                    .thenThrow(new MarketplaceApiException("Chrome failed to launch"));

            SearchResult result = search.search(asinIn("GB"));

            assertThat(result.getItems()).isEmpty();
            assertThat(result.getRegionsUnavailable()).isNotEmpty();
            assertThat(result.getNote()).contains("could not be reached");
        }
    }

    @Nested
    @DisplayName("domain mapping")
    class Domains {

        @Test
        @DisplayName("country codes map to the service's domains, and back")
        void mapping() {
            assertThat(AmazonScraperClient.domainFor("GB")).isEqualTo("co.uk");
            assertThat(AmazonScraperClient.domainFor("IN")).isEqualTo("in");
            assertThat(AmazonScraperClient.domainFor("US")).isEqualTo("com");
            assertThat(AmazonScraperClient.domainFor("FR")).isNull();

            assertThat(AmazonScraperClient.countryForDomain("co.uk")).isEqualTo("GB");
            assertThat(AmazonScraperClient.countryForDomain("de")).isEqualTo("DE");
            assertThat(AmazonScraperClient.countryForDomain("fr")).isNull();
        }

        @Test
        @DisplayName("the request names the storefront asked for")
        void domainIsPassedThrough() {
            when(client.getProduct(anyString(), anyString())).thenReturn(NATIVE_IN);

            search.search(asinIn("DE"));

            verify(client).getProduct("B08RC6981L", "de");
            verify(client, never()).getProduct(anyString(), eq("com"));
        }
    }
}
