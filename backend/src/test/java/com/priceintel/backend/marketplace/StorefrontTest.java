package com.priceintel.backend.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.priceintel.backend.exception.MarketplaceApiException;
import com.priceintel.backend.marketplace.adapter.AmazonAdapter;
import com.priceintel.backend.marketplace.amazon.AmazonMarketplace;
import com.priceintel.backend.marketplace.amazon.AmazonProductNormalizer;
import com.priceintel.backend.marketplace.amazon.AmazonSpApiClient;
import com.priceintel.backend.marketplace.amazon.AmazonSpApiProperties;
import com.priceintel.backend.marketplace.amazon.LwaTokenService;
import com.priceintel.backend.utils.MarketplaceUrlParser;
import com.priceintel.backend.utils.Storefront;

/**
 * A listing belongs to one country's storefront.
 *
 * <p>The defect this guards: a listing found on amazon.ca at CA$140 was attached
 * and stored as amazon.com's US$144.29, because the attach carried no region and
 * the lookup hunted across storefronts for the first one with a price.</p>
 */
class StorefrontTest {

    @Nested
    @DisplayName("resolving a storefront")
    class Resolving {

        @Test
        @DisplayName("codes, Amazon marketplace ids and eBay site ids all normalise to a country")
        void normalises() {
            assertThat(Storefront.normalise("ca")).isEqualTo("CA");
            assertThat(Storefront.normalise(" US ")).isEqualTo("US");
            assertThat(Storefront.normalise("A2EUQ1WTGCTBG2")).isEqualTo("CA");
            assertThat(Storefront.normalise("EBAY_GB")).isEqualTo("GB");
        }

        @Test
        @DisplayName("the UK has one spelling — GB — however it arrives")
        void ukIsGb() {
            assertThat(Storefront.normalise("UK")).isEqualTo("GB");
            assertThat(Storefront.normalise("GB")).isEqualTo("GB");
            assertThat(Storefront.normalise(AmazonMarketplace.UK.getMarketplaceId())).isEqualTo("GB");
        }

        @Test
        @DisplayName("nonsense is not a storefront")
        void rejectsNonsense() {
            assertThat(Storefront.normalise("CANADA")).isNull();
            assertThat(Storefront.normalise("")).isNull();
            assertThat(Storefront.normalise(null)).isNull();
        }

        @Test
        @DisplayName("URLs are read by host, including multi-part domains")
        void fromUrl() {
            assertThat(Storefront.fromUrl("https://www.amazon.ca/dp/B0DGHMNQ5Z")).isEqualTo("CA");
            assertThat(Storefront.fromUrl("https://www.amazon.com/dp/B0DGHMNQ5Z")).isEqualTo("US");
            assertThat(Storefront.fromUrl("https://www.amazon.co.uk/dp/B0DGHMNQ5Z")).isEqualTo("GB");
            // Not mistaken for amazon.com.
            assertThat(Storefront.fromUrl("https://www.amazon.com.au/dp/B0DGHMNQ5Z")).isEqualTo("AU");
            assertThat(Storefront.fromUrl("https://www.ebay.ca/itm/123456789012")).isEqualTo("CA");
            assertThat(Storefront.fromUrl("https://www.ebay.com.au/itm/123456789012")).isEqualTo("AU");
            assertThat(Storefront.fromUrl("not a url")).isNull();
        }

        @Test
        @DisplayName("EUR is never guessed — it belongs to too many storefronts")
        void eurIsAmbiguous() {
            assertThat(Storefront.fromCurrency("CAD")).isEqualTo("CA");
            assertThat(Storefront.fromCurrency("EUR")).isNull();
        }

        @Test
        @DisplayName("reported beats URL beats currency beats the default")
        void precedence() {
            // A reported storefront wins even against a URL that says otherwise.
            assertThat(Storefront.resolve("CA", "https://www.amazon.com/dp/X", "USD")).isEqualTo("CA");
            assertThat(Storefront.resolve(null, "https://www.amazon.ca/dp/X", "USD")).isEqualTo("CA");
            assertThat(Storefront.resolve(null, null, "CAD")).isEqualTo("CA");
            assertThat(Storefront.resolve(null, null, null)).isEqualTo("US");
        }
    }

    @Nested
    @DisplayName("building links")
    class Links {

        @Test
        @DisplayName("a rebuilt link opens the listing's own storefront")
        void linksFollowStorefront() {
            assertThat(MarketplaceUrlParser.build("AMAZON", "B0DGHMNQ5Z", "CA"))
                    .startsWith("https://www.amazon.ca/");
            assertThat(MarketplaceUrlParser.build("EBAY", "123456789012", "GB"))
                    .startsWith("https://www.ebay.co.uk/");
            assertThat(MarketplaceUrlParser.build("EBAY", "123456789012", null))
                    .startsWith("https://www.ebay.com/");
        }
    }

    @Nested
    @DisplayName("pinned Amazon lookups")
    class PinnedAmazon {

        private final AmazonSpApiClient client = mock(AmazonSpApiClient.class);
        private final AmazonSpApiProperties props = new AmazonSpApiProperties();
        private final AmazonAdapter adapter = new AmazonAdapter(client,
                new AmazonProductNormalizer(new ObjectMapper()), props, mock(LwaTokenService.class),
                mock(com.priceintel.backend.marketplace.scraper.AmazonScraperSearch.class));

        private static final String CATALOG = """
                {"asin":"B0DGHMNQ5Z","summaries":[{"marketplaceId":"A2EUQ1WTGCTBG2","itemName":"Echo Dot"}]}
                """;
        private static final String PRICING_CAD = """
                {"payload":[{"ASIN":"B0DGHMNQ5Z","Product":{"CompetitivePricing":{"CompetitivePrices":[
                  {"Price":{"LandedPrice":{"CurrencyCode":"CAD","Amount":140.00},
                            "ListingPrice":{"CurrencyCode":"CAD","Amount":140.00}}}]}}}]}
                """;

        @Test
        @DisplayName("a named storefront is the only one asked — no hunting")
        void pinnedListingAsksOnlyThatStorefront() {
            props.setOfferFallbackEnabled(false);
            when(client.getCatalogItem("B0DGHMNQ5Z", AmazonMarketplace.CA)).thenReturn(CATALOG);
            when(client.getPricing("B0DGHMNQ5Z", AmazonMarketplace.CA)).thenReturn(PRICING_CAD);

            var details = adapter.getListing("B0DGHMNQ5Z", "CA");

            assertThat(details.getCountryCode()).isEqualTo("CA");
            assertThat(details.getUrl()).contains("amazon.ca");
            // amazon.com was never consulted, however it might have answered.
            verify(client, never()).getCatalogItem(anyString(), eq(AmazonMarketplace.US));
            verify(client, never()).getPricing(anyString(), eq(AmazonMarketplace.US));
        }

        @Test
        @DisplayName("an unpriced pinned listing stays unpriced rather than borrowing another shop's price")
        void pinnedListingDoesNotFallBack() {
            props.setOfferFallbackEnabled(false);
            when(client.getCatalogItem("B0DGHMNQ5Z", AmazonMarketplace.CA)).thenReturn(CATALOG);
            when(client.getPricing("B0DGHMNQ5Z", AmazonMarketplace.CA)).thenReturn("{\"payload\":[]}");

            var details = adapter.getListing("B0DGHMNQ5Z", "CA");

            assertThat(details.getPrice()).isNull();
            verify(client, never()).getPricing(anyString(), eq(AmazonMarketplace.US));
        }

        @Test
        @DisplayName("a storefront Amazon has no marketplace for is refused, not substituted")
        void unknownStorefrontRefused() {
            assertThatThrownBy(() -> adapter.getListing("B0DGHMNQ5Z", "ZZ"))
                    .isInstanceOf(MarketplaceApiException.class)
                    .hasMessageContaining("ZZ");
            verify(client, never()).getCatalogItem(anyString(), any());
        }
    }
}
