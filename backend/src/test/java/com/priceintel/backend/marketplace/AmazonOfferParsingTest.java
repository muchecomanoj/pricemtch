package com.priceintel.backend.marketplace;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceintel.backend.marketplace.amazon.AmazonMarketplace;
import com.priceintel.backend.marketplace.amazon.AmazonProductNormalizer;
import com.priceintel.backend.marketplace.model.OfferListing;

/**
 * Parsing of the SP-API getItemOffers payload — the call that recovers a price
 * when no seller holds the Buy Box and competitivePrice returns nothing.
 */
class AmazonOfferParsingTest {

    private final AmazonProductNormalizer normalizer = new AmazonProductNormalizer(new ObjectMapper());

    /** Note: unlike competitivePrice, getItemOffers returns payload as an OBJECT. */
    private static final String OFFERS_JSON = """
            {
              "payload": {
                "ASIN": "B07BFFCH1Y",
                "status": "Success",
                "ItemCondition": "New",
                "Offers": [
                  {
                    "SellerId": "A2SELLERTWO",
                    "SubCondition": "new",
                    "ListingPrice": { "CurrencyCode": "GBP", "Amount": 24.50 },
                    "Shipping":     { "CurrencyCode": "GBP", "Amount": 3.99 },
                    "IsBuyBoxWinner": false,
                    "IsFulfilledByAmazon": false,
                    "SellerFeedbackRating": { "SellerPositiveFeedbackRating": 91.0, "FeedbackCount": 120 }
                  },
                  {
                    "SellerId": "A1SELLERONE",
                    "SubCondition": "new",
                    "ListingPrice": { "CurrencyCode": "GBP", "Amount": 19.99 },
                    "Shipping":     { "CurrencyCode": "GBP", "Amount": 0 },
                    "IsBuyBoxWinner": true,
                    "IsFulfilledByAmazon": true,
                    "SellerFeedbackRating": { "SellerPositiveFeedbackRating": 98.5, "FeedbackCount": 4210 }
                  }
                ]
              }
            }
            """;

    @Test
    void everyCompetingSellerIsParsed() {
        List<OfferListing> offers = normalizer.toOffers(OFFERS_JSON, "B07BFFCH1Y", AmazonMarketplace.UK);

        assertThat(offers).hasSize(2)
                .extracting(OfferListing::getSellerId)
                .containsExactlyInAnyOrder("A1SELLERONE", "A2SELLERTWO");
    }

    @Test
    void landedPriceAddsShippingSoOffersAreComparable() {
        List<OfferListing> offers = normalizer.toOffers(OFFERS_JSON, "B07BFFCH1Y", AmazonMarketplace.UK);

        OfferListing withShipping = offers.stream()
                .filter(o -> "A2SELLERTWO".equals(o.getSellerId())).findFirst().orElseThrow();

        assertThat(withShipping.getListingPrice()).isEqualByComparingTo("24.50");
        assertThat(withShipping.getShipping()).isEqualByComparingTo("3.99");
        assertThat(withShipping.getLandedPrice()).isEqualByComparingTo("28.49");
    }

    @Test
    void offersAreSortedByCheapestLandedPrice() {
        List<OfferListing> offers = normalizer.toOffers(OFFERS_JSON, "B07BFFCH1Y", AmazonMarketplace.UK);

        assertThat(offers).first().satisfies(o ->
                assertThat(o.getSellerId()).isEqualTo("A1SELLERONE"));
    }

    @Test
    void sellerAndFulfilmentDetailAreCaptured() {
        OfferListing buyBox = normalizer.toOffers(OFFERS_JSON, "B07BFFCH1Y", AmazonMarketplace.UK).get(0);

        assertThat(buyBox.isBuyBoxWinner()).isTrue();
        assertThat(buyBox.isFulfilledByMarketplace()).isTrue();
        assertThat(buyBox.getSellerPositiveFeedbackPercent()).isEqualTo(98.5);
        assertThat(buyBox.getSellerFeedbackCount()).isEqualTo(4210);
        assertThat(buyBox.getCurrency()).isEqualTo("GBP");
        assertThat(buyBox.getCountryCode()).isEqualTo("GB");
    }

    @Test
    void buyBoxWinnerIsPreferredAsThePrice() {
        BigDecimal price = normalizer.lowestOfferPrice(
                normalizer.toOffers(OFFERS_JSON, "B07BFFCH1Y", AmazonMarketplace.UK));

        assertThat(price).isEqualByComparingTo("19.99");
    }

    /** The case this whole feature exists for: offers but no Buy Box winner. */
    @Test
    void aPriceIsStillFoundWhenNobodyHoldsTheBuyBox() {
        String noBuyBox = """
                {
                  "payload": {
                    "ASIN": "B00R367XFE",
                    "ItemCondition": "New",
                    "Offers": [
                      {
                        "SellerId": "A3ONLY",
                        "ListingPrice": { "CurrencyCode": "GBP", "Amount": 12.75 },
                        "Shipping":     { "CurrencyCode": "GBP", "Amount": 2.00 },
                        "IsBuyBoxWinner": false
                      }
                    ]
                  }
                }
                """;

        List<OfferListing> offers = normalizer.toOffers(noBuyBox, "B00R367XFE", AmazonMarketplace.UK);

        assertThat(offers).hasSize(1);
        assertThat(normalizer.lowestOfferPrice(offers)).isEqualByComparingTo("12.75");
    }

    @Test
    void anEmptyOrMissingPayloadYieldsNoOffersRatherThanAnError() {
        assertThat(normalizer.toOffers(null, "X", AmazonMarketplace.US)).isEmpty();
        assertThat(normalizer.toOffers("{}", "X", AmazonMarketplace.US)).isEmpty();
        assertThat(normalizer.toOffers("{\"payload\":{\"Offers\":[]}}", "X", AmazonMarketplace.US)).isEmpty();
        assertThat(normalizer.lowestOfferPrice(List.of())).isNull();
    }

    @Test
    void offersWithoutAPriceAreSkipped() {
        String malformed = """
                {"payload":{"Offers":[{"SellerId":"A1","IsBuyBoxWinner":true}]}}
                """;

        assertThat(normalizer.toOffers(malformed, "X", AmazonMarketplace.US)).isEmpty();
    }
}
