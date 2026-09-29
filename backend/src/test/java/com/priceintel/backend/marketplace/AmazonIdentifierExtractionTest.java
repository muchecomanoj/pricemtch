package com.priceintel.backend.marketplace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceintel.backend.marketplace.amazon.AmazonMarketplace;
import com.priceintel.backend.marketplace.amazon.AmazonProductNormalizer;
import com.priceintel.backend.marketplace.model.ItemIdentifier;

/**
 * Surfacing the barcodes Amazon already returns.
 *
 * <p>{@code identifiers} has always been requested on every catalogue call and
 * always thrown away by the search mapping — so a user could search BY a UPC and
 * never learn the UPC of what came back. These pin the extraction, including the
 * per-marketplace nesting that makes it easy to attach a European barcode to a
 * US listing.</p>
 */
class AmazonIdentifierExtractionTest {

    private final AmazonProductNormalizer normalizer =
            new AmazonProductNormalizer(new ObjectMapper());

    /** One ASIN carrying different barcodes in the US and UK catalogues. */
    private static final String SEARCH_JSON = """
            {
              "numberOfResults": 1,
              "items": [
                {
                  "asin": "B0DGHMNQ5Z",
                  "summaries": [ { "itemName": "Apple AirPods 4", "brand": "Apple" } ],
                  "identifiers": [
                    {
                      "marketplaceId": "ATVPDKIKX0DER",
                      "identifiers": [
                        { "identifierType": "UPC", "identifier": "195949723001" },
                        { "identifierType": "EAN", "identifier": "0195949723001" },
                        { "identifierType": "UPC", "identifier": "195949723001" }
                      ]
                    },
                    {
                      "marketplaceId": "A1F83G8C2ARO7P",
                      "identifiers": [
                        { "identifierType": "EAN", "identifier": "9999999999999" }
                      ]
                    }
                  ]
                }
              ]
            }
            """;

    @Test
    @DisplayName("the ASIN leads, then the barcodes for that marketplace")
    void extractsIdentifiersForTheQueriedMarketplace() {
        var result = normalizer.toSearchResult(SEARCH_JSON, "airpods", AmazonMarketplace.US);
        var ids = result.getItems().get(0).getIdentifiers();

        assertThat(ids).extracting(ItemIdentifier::getType, ItemIdentifier::getValue)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple("ASIN", "B0DGHMNQ5Z"),
                        org.assertj.core.api.Assertions.tuple("UPC", "195949723001"),
                        org.assertj.core.api.Assertions.tuple("EAN", "0195949723001"));
    }

    @Test
    @DisplayName("another region's barcode never leaks into this one")
    void ignoresOtherMarketplaces() {
        var us = normalizer.toSearchResult(SEARCH_JSON, "airpods", AmazonMarketplace.US);
        assertThat(us.getItems().get(0).getIdentifiers())
                .extracting(ItemIdentifier::getValue).doesNotContain("9999999999999");

        // The same payload read as UK yields that marketplace's code instead.
        var uk = normalizer.toSearchResult(SEARCH_JSON, "airpods", AmazonMarketplace.UK);
        assertThat(uk.getItems().get(0).getIdentifiers())
                .extracting(ItemIdentifier::getValue)
                .containsExactly("B0DGHMNQ5Z", "9999999999999");
    }

    @Test
    @DisplayName("a listing detail carries them too")
    void listingDetailsCarryIdentifiers() {
        String itemJson = """
                {
                  "asin": "B0DGHMNQ5Z",
                  "summaries": [ { "itemName": "Apple AirPods 4", "brand": "Apple" } ],
                  "identifiers": [
                    { "marketplaceId": "ATVPDKIKX0DER",
                      "identifiers": [ { "identifierType": "UPC", "identifier": "195949723001" } ] }
                  ]
                }
                """;
        var details = normalizer.toListingDetails(itemJson, null, "B0DGHMNQ5Z", AmazonMarketplace.US);

        assertThat(details.getIdentifiers())
                .extracting(ItemIdentifier::getType).containsExactly("ASIN", "UPC");
    }

    @Test
    @DisplayName("no barcodes published still yields the ASIN, never null")
    void alwaysAtLeastTheAsin() {
        String bare = """
                { "numberOfResults": 1,
                  "items": [ { "asin": "B0DGHMNQ5Z",
                               "summaries": [ { "itemName": "Apple AirPods 4" } ] } ] }
                """;
        var ids = normalizer.toSearchResult(bare, "airpods", AmazonMarketplace.US)
                .getItems().get(0).getIdentifiers();

        assertThat(ids).extracting(ItemIdentifier::getType).containsExactly("ASIN");
    }
}
