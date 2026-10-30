package com.priceintel.backend.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceintel.backend.exception.MarketplaceApiException;
import com.priceintel.backend.marketplace.model.ListingDetails;
import com.priceintel.backend.marketplace.model.OfferListing;
import com.priceintel.backend.marketplace.model.PriceSnapshot;
import com.priceintel.backend.marketplace.scraper.AmazonScraperClient;
import com.priceintel.backend.marketplace.scraper.AmazonScraperNormalizer;
import com.priceintel.backend.marketplace.scraper.AmazonScraperProperties;
import com.priceintel.backend.marketplace.scraper.AmazonScraperSearch;

/**
 * The listing screen's reads for storefronts SP-API cannot reach.
 *
 * <p>Search has read the public page for these marketplaces from the start; the
 * listing screen had no such fallback, so a UK search found competitors and then
 * opening one answered "Amazon United Kingdom is not connected yet".</p>
 *
 * <p>The payloads are the ones the reader actually returns, including the
 * awkward case this feature turns on: a UK page quoted in rupees.</p>
 */
class AmazonScraperListingReadTest {

    private final AmazonScraperClient client = mock(AmazonScraperClient.class);
    private final AmazonScraperProperties props = new AmazonScraperProperties();
    private final AmazonScraperSearch reader = new AmazonScraperSearch(
            props, client, new AmazonScraperNormalizer(new ObjectMapper()));

    AmazonScraperListingReadTest() {
        props.setEnabled(true);
        props.setBaseUrl("http://192.168.10.189:5000");
        props.setAcceptConvertedPrices(true);
    }

    /** amazon.co.uk quoting INR because the reader sits outside the UK. */
    private static final String CONVERTED_UK = """
            {"asin":"B08RC6981L","found":true,"marketplace":"co.uk",
             "product_name":"INFURTURE H1 Active Noise Cancelling Headphones",
             "details":{"brand":"INFURTURE","UPC":"788404793994","Model Number":"BN601A"},
             "price":{"amount":29.94,"currency":"GBP","converted":true,"raw":"£29.94",
                      "original":{"amount":3837.7,"currency":"INR","raw":"INR 3,837.70"},
                      "fx":{"rate":0.007802,"source":"open.er-api.com"}},
             "price_status":"converted"}
            """;

    /** The India storefront quoting its own currency — an observed price. */
    private static final String NATIVE_IN = """
            {"asin":"B0DDHM6D3L","found":true,"marketplace":"in",
             "product_name":"Portronics Conch Theta C in Ear Wired Earphones",
             "details":{"brand":"Portronics","UPC":"8908010947287"},
             "price":{"amount":311.0,"currency":"INR","converted":false,"raw":"₹311.00"},
             "price_status":"ok"}
            """;

    private static final String NOT_FOUND = """
            {"asin":"B000MISSING","found":false,"marketplace":"co.uk"}
            """;

    @Nested
    @DisplayName("which storefronts the reader answers for")
    class Routing {

        @Test
        @DisplayName("a storefront outside the SP-API authorisation is read from the page")
        void unauthorisedStorefrontIsHandled() {
            assertThat(reader.handlesStorefront("GB")).isTrue();
            assertThat(reader.handlesStorefront("DE")).isTrue();
            assertThat(reader.handlesStorefront("IN")).isTrue();
        }

        @Test
        @DisplayName("an authorised storefront stays with SP-API, which reads it properly")
        void spApiStorefrontsAreLeftAlone() {
            assertThat(reader.handlesStorefront("US")).isFalse();
            assertThat(reader.handlesStorefront("CA")).isFalse();
        }

        @Test
        @DisplayName("nothing is handled when the reader is switched off")
        void disabledReaderHandlesNothing() {
            AmazonScraperProperties off = new AmazonScraperProperties();
            off.setEnabled(false);
            AmazonScraperSearch quiet = new AmazonScraperSearch(
                    off, client, new AmazonScraperNormalizer(new ObjectMapper()));

            assertThat(quiet.handlesStorefront("GB")).isFalse();
        }

        @Test
        @DisplayName("an unknown or missing storefront is not handled")
        void unknownStorefrontIsNotHandled() {
            assertThat(reader.handlesStorefront("ZZ")).isFalse();
            assertThat(reader.handlesStorefront(null)).isFalse();
            assertThat(reader.handlesStorefront("")).isFalse();
        }
    }

    @Nested
    @DisplayName("listing details")
    class Listing {

        @Test
        @DisplayName("a converted price is carried, but flagged and explained")
        void convertedPriceIsFlagged() {
            when(client.getProduct(eq("B08RC6981L"), anyString())).thenReturn(CONVERTED_UK);

            ListingDetails details = reader.listing("B08RC6981L", "GB");

            assertThat(details.getTitle()).isEqualTo("INFURTURE H1 Active Noise Cancelling Headphones");
            assertThat(details.getPrice()).isEqualByComparingTo("29.94");
            // The number alone would be read as amazon.co.uk's shelf price. It
            // is an import quote carrying duty and international shipping.
            assertThat(details.isPriceEstimated()).isTrue();
            assertThat(details.getPriceNote()).contains("import duty");
            assertThat(details.getSource()).isEqualTo("PAGE_READER");
            assertThat(details.getCountryCode()).isEqualTo("GB");
        }

        @Test
        @DisplayName("a price the storefront quoted itself is not flagged as an estimate")
        void nativePriceIsNotFlagged() {
            when(client.getProduct(eq("B0DDHM6D3L"), anyString())).thenReturn(NATIVE_IN);

            ListingDetails details = reader.listing("B0DDHM6D3L", "IN");

            assertThat(details.getPrice()).isEqualByComparingTo("311.0");
            assertThat(details.isPriceEstimated()).isFalse();
            assertThat(details.getPriceNote()).isNull();
        }

        @Test
        @DisplayName("the identifiers on the page come back with the listing")
        void identifiersAreKept() {
            when(client.getProduct(eq("B08RC6981L"), anyString())).thenReturn(CONVERTED_UK);

            assertThat(reader.listing("B08RC6981L", "GB").getIdentifiers())
                    .extracting(i -> i.getType() + " " + i.getValue())
                    .contains("ASIN B08RC6981L", "UPC 788404793994");
        }

        @Test
        @DisplayName("condition and seller are left unknown rather than guessed")
        void unknownFieldsStayNull() {
            when(client.getProduct(eq("B08RC6981L"), anyString())).thenReturn(CONVERTED_UK);

            ListingDetails details = reader.listing("B08RC6981L", "GB");

            // A guessed condition is the one value that passes a condition
            // check silently.
            assertThat(details.getCondition()).isNull();
            assertThat(details.getSeller()).isNull();
        }

        @Test
        @DisplayName("a product the page does not have fails with a sentence, not a blank page")
        void missingProductExplainsItself() {
            when(client.getProduct(anyString(), anyString())).thenReturn(NOT_FOUND);

            assertThatThrownBy(() -> reader.listing("B000MISSING", "GB"))
                    .isInstanceOf(MarketplaceApiException.class)
                    .hasMessageContaining("no listing")
                    .hasMessageContaining("GB");
        }

        @Test
        @DisplayName("a reader that cannot be reached says so, naming both reasons")
        void readerFailureIsExplained() {
            when(client.getProduct(anyString(), anyString()))
                    .thenThrow(new MarketplaceApiException("connection refused"));

            assertThatThrownBy(() -> reader.listing("B08RC6981L", "GB"))
                    .isInstanceOf(MarketplaceApiException.class)
                    .hasMessageContaining("outside the SP-API authorisation")
                    .hasMessageContaining("connection refused");
        }
    }

    @Nested
    @DisplayName("price and offers")
    class PriceAndOffers {

        @Test
        @DisplayName("the price snapshot carries the same flag as the listing")
        void priceSnapshotIsFlagged() {
            when(client.getProduct(eq("B08RC6981L"), anyString())).thenReturn(CONVERTED_UK);

            PriceSnapshot snapshot = reader.price("B08RC6981L", "GB");

            assertThat(snapshot.getItemPrice()).isEqualByComparingTo("29.94");
            // One quoted figure, delivered. Splitting it into item and shipping
            // would invent a breakdown the page never gave.
            assertThat(snapshot.getLandedPrice()).isEqualByComparingTo("29.94");
            assertThat(snapshot.isPriceEstimated()).isTrue();
            assertThat(snapshot.getSource()).isEqualTo("PAGE_READER");
        }

        @Test
        @DisplayName("offers hold the buy-box only, and say that is all there is")
        void offersAreTheBuyBoxOnly() {
            when(client.getProduct(eq("B08RC6981L"), anyString())).thenReturn(CONVERTED_UK);

            java.util.List<OfferListing> offers = reader.offers("B08RC6981L", "GB");

            assertThat(offers).hasSize(1);
            OfferListing offer = offers.get(0);
            assertThat(offer.isBuyBoxWinner()).isTrue();
            // One row must not read as "this product has one seller".
            assertThat(offer.getPriceNote()).contains("only the winning offer");
            assertThat(offer.isPriceEstimated()).isTrue();
        }

        @Test
        @DisplayName("a page with no price yields no offer rather than a priceless one")
        void noPriceMeansNoOffer() {
            when(client.getProduct(anyString(), anyString())).thenReturn("""
                    {"asin":"B08RC6981L","found":true,"marketplace":"co.uk",
                     "product_name":"Out of stock thing","price_status":"unavailable"}
                    """);

            assertThat(reader.offers("B08RC6981L", "GB")).isEmpty();
        }

        @Test
        @DisplayName("a converted price is left out entirely when they are not accepted")
        void convertedPriceCanBeRefused() {
            AmazonScraperProperties strict = new AmazonScraperProperties();
            strict.setEnabled(true);
            strict.setBaseUrl("http://192.168.10.189:5000");
            strict.setAcceptConvertedPrices(false);
            AmazonScraperSearch picky = new AmazonScraperSearch(
                    strict, client, new AmazonScraperNormalizer(new ObjectMapper()));
            when(client.getProduct(eq("B08RC6981L"), anyString())).thenReturn(CONVERTED_UK);

            ListingDetails details = picky.listing("B08RC6981L", "GB");

            assertThat(details.getPrice()).isNull();
            assertThat(details.isAvailable()).isFalse();
            assertThat(details.getTitle()).isNotNull();   // the rest still reads
        }
    }

    @Nested
    @DisplayName("when the reader answers from a different storefront than asked")
    class WrongStorefront {

        /** The service treats `domain` as a starting point and can fall through. */
        private static final String UK_ASKED_DE_ANSWERED = """
                {"asin":"B08RC6981L","found":true,"marketplace":"de",
                 "product_name":"INFURTURE H1","details":{},
                 "price":{"amount":27.5,"currency":"EUR"},"price_status":"ok"}
                """;

        @Test
        @DisplayName("the storefront recorded is the one that answered, and it is said plainly")
        void servedStorefrontWins() {
            when(client.getProduct(anyString(), anyString())).thenReturn(UK_ASKED_DE_ANSWERED);

            ListingDetails details = reader.listing("B08RC6981L", "GB");

            // Filing German data under amazon.co.uk is the defect this avoids.
            assertThat(details.getCountryCode()).isEqualTo("DE");
            assertThat(details.getPriceNote()).contains("Asked for Amazon GB");
        }
    }
}
