package com.priceintel.backend.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.priceintel.backend.dto.request.MarketplaceSearchRequest;
import com.priceintel.backend.marketplace.amazon.AmazonMarketplace;
import com.priceintel.backend.marketplace.amazon.AmazonSpApiProperties;
import com.priceintel.backend.marketplace.model.SearchResult;
import com.priceintel.backend.marketplace.model.SearchResultItem;
import com.priceintel.backend.service.impl.MarketplaceServiceImpl;

/**
 * Covers the "never show a product without a price" rule: the marketplace order
 * a lookup walks, and the service-level filter that drops priceless listings.
 */
class AmazonRegionFallbackTest {

    // ---------- marketplace resolution ----------

    @Test
    void configuredMarketplaceIsTriedFirst() {
        AmazonSpApiProperties props = new AmazonSpApiProperties();
        props.setMarketplaceId(AmazonMarketplace.UK.getMarketplaceId());

        List<AmazonMarketplace> order = props.resolveSearchMarketplaces();

        assertThat(order).first().isEqualTo(AmazonMarketplace.UK);
    }

    @Test
    void primaryIsNotRepeatedInTheFallbackList() {
        AmazonSpApiProperties props = new AmazonSpApiProperties();
        props.setMarketplaceId(AmazonMarketplace.US.getMarketplaceId());
        props.setSearchMarketplaceIds(List.of("US", "UK", "US", "DE"));

        List<AmazonMarketplace> order = props.resolveSearchMarketplaces();

        assertThat(order).containsExactly(
                AmazonMarketplace.US, AmazonMarketplace.UK, AmazonMarketplace.DE);
    }

    @Test
    void theUkIsInTheDefaultFallbackOrderRightAfterTheUsPrimary() {
        AmazonSpApiProperties props = new AmazonSpApiProperties();
        props.setMarketplaceId(AmazonMarketplace.US.getMarketplaceId());

        List<AmazonMarketplace> order = props.resolveSearchMarketplaces();

        assertThat(order).startsWith(AmazonMarketplace.US, AmazonMarketplace.UK);
        assertThat(AmazonMarketplace.UK.getCountryCode()).isEqualTo("GB");
    }

    @Test
    void fallbackDisabledQueriesOnlyThePrimaryMarketplace() {
        AmazonSpApiProperties props = new AmazonSpApiProperties();
        props.setMarketplaceId(AmazonMarketplace.DE.getMarketplaceId());
        props.setRegionFallbackEnabled(false);

        assertThat(props.resolveSearchMarketplaces()).containsExactly(AmazonMarketplace.DE);
    }

    @Test
    void marketplacesAreAcceptedAsIdOrCountryCode() {
        assertThat(AmazonMarketplace.find("A1F83G8C2ARO7P")).contains(AmazonMarketplace.UK);
        assertThat(AmazonMarketplace.find("GB")).contains(AmazonMarketplace.UK);
        assertThat(AmazonMarketplace.find("JP")).contains(AmazonMarketplace.JP);
        assertThat(AmazonMarketplace.find("nonsense")).isEmpty();
    }

    @Test
    void europeanMarketplacesUseTheEuHost() {
        assertThat(AmazonMarketplace.UK.getEndpoint()).contains("-eu.");
        assertThat(AmazonMarketplace.US.getEndpoint()).contains("-na.");
        assertThat(AmazonMarketplace.JP.getEndpoint()).contains("-fe.");
    }

    // ---------- service-level price filter ----------

    @Test
    void listingsWithoutAPriceAreHiddenFromSearchResults() {
        SearchResult raw = resultWith(
                item("A1", new BigDecimal("24.99")),
                item("A2", null),
                item("A3", BigDecimal.ZERO));

        SearchResult filtered = searchVia(raw, MarketplaceSearchRequest.builder().query("paint").maxResults(10).build());

        assertThat(filtered.getItems()).extracting(SearchResultItem::getMarketplaceItemId)
                .containsExactly("A1");
        assertThat(filtered.getTotalResults()).isEqualTo(1);
        assertThat(filtered.getNote()).contains("without a price");
    }

    @Test
    void optingOutKeepsUnpricedListings() {
        SearchResult raw = resultWith(item("A1", new BigDecimal("24.99")), item("A2", null));

        SearchResult filtered = searchVia(raw, MarketplaceSearchRequest.builder().query("paint").maxResults(10).requirePrice(false).build());

        assertThat(filtered.getItems()).hasSize(2);
    }

    @Test
    void anAllUnpricedResultSetComesBackEmptyRatherThanPriceless() {
        SearchResult raw = resultWith(item("A1", null), item("A2", null));

        SearchResult filtered = searchVia(raw, MarketplaceSearchRequest.builder().query("paint").maxResults(10).build());

        assertThat(filtered.getItems()).isEmpty();
        assertThat(filtered.getTotalResults()).isZero();
    }

    // ---------- helpers ----------

    private SearchResult searchVia(SearchResult adapterResult, MarketplaceSearchRequest request) {
        MarketplaceAdapter adapter = mock(MarketplaceAdapter.class);
        when(adapter.search(any())).thenReturn(adapterResult);

        MarketplaceAdapterFactory factory = mock(MarketplaceAdapterFactory.class);
        when(factory.getAdapter(Marketplace.AMAZON)).thenReturn(adapter);

        return new MarketplaceServiceImpl(factory).search(Marketplace.AMAZON, request);
    }

    private SearchResult resultWith(SearchResultItem... items) {
        return SearchResult.builder()
                .marketplace(Marketplace.AMAZON)
                .query("paint")
                .items(new java.util.ArrayList<>(List.of(items)))
                .totalResults(items.length)
                .build();
    }

    private SearchResultItem item(String asin, BigDecimal price) {
        return SearchResultItem.builder()
                .marketplaceItemId(asin).title("Item " + asin).price(price).build();
    }
}
