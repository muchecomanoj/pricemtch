package com.priceintel.backend.marketplace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.priceintel.backend.entity.MarketplaceSearchCache;

/**
 * The cache key is what makes a fetch shareable between clients: two clients
 * tracking the same product must normalise to the same key, or the second one
 * pays for an API call that has already been made.
 */
class SearchCacheKeyTest {

    @Test
    void theSameIdentifierFromTwoClientsProducesOneKey() {
        String clientOne = MarketplaceSearchCache.normalizeKey("B0DBHWQ58D");
        String clientTwo = MarketplaceSearchCache.normalizeKey(" b0dbhwq58d ");

        assertThat(clientOne).isEqualTo(clientTwo);
    }

    @Test
    void whitespaceAndCaseDifferencesDoNotSplitTheCache() {
        assertThat(MarketplaceSearchCache.normalizeKey("Samsung  653L   Fridge"))
                .isEqualTo(MarketplaceSearchCache.normalizeKey("samsung 653l fridge"));
    }

    @Test
    void genuinelyDifferentProductsKeepSeparateKeys() {
        assertThat(MarketplaceSearchCache.normalizeKey("B0DBHWQ58D"))
                .isNotEqualTo(MarketplaceSearchCache.normalizeKey("B07BFFCH1Y"));
    }

    @Test
    void aNullQueryIsHandledRatherThanThrowing() {
        assertThat(MarketplaceSearchCache.normalizeKey(null)).isEmpty();
    }

    @Test
    void itemIdsRoundTripThroughStorage() {
        MarketplaceSearchCache entry = MarketplaceSearchCache.builder().build();
        entry.setItemIdList(java.util.List.of("B01", "B02", "B03"));

        assertThat(entry.getItemIds()).isEqualTo("B01,B02,B03");
        assertThat(entry.itemIdList()).containsExactly("B01", "B02", "B03");
    }

    @Test
    void anEmptyOrMalformedItemListDegradesToNoResults() {
        MarketplaceSearchCache entry = MarketplaceSearchCache.builder().build();
        assertThat(entry.itemIdList()).isEmpty();

        entry.setItemIds(" , ,, ");
        assertThat(entry.itemIdList()).isEmpty();
    }
}
