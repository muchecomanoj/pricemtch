package com.priceintel.backend.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.priceintel.backend.constants.MatchStatus;
import com.priceintel.backend.dto.response.SalesResponse;
import com.priceintel.backend.entity.CompetitorListing;
import com.priceintel.backend.marketplace.Marketplace;
import com.priceintel.backend.marketplace.model.SalesMetrics;
import com.priceintel.backend.repository.CompetitorListingRepository;
import com.priceintel.backend.repository.ProductRepository;
import com.priceintel.backend.service.MarketplaceService;
import com.priceintel.backend.service.impl.SalesService;

/**
 * Competitor sales figures, and what to say when there are none.
 *
 * <p>Neither connected marketplace reports competitor unit sales: Amazon's
 * SP-API does not expose it, and eBay's sales history sits behind the
 * restricted Marketplace Insights API. The response must say that, not show a
 * zero dressed as an estimate.</p>
 */
class SalesEstimateTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final CompetitorListingRepository listings = mock(CompetitorListingRepository.class);
    private final MarketplaceService marketplaces = mock(MarketplaceService.class);
    private final SalesService service = new SalesService(products, listings, marketplaces);

    private static CompetitorListing matched(String marketplace, String itemId) {
        CompetitorListing l = new CompetitorListing();
        l.setMarketplace(marketplace);
        l.setMarketplaceItemId(itemId);
        l.setMatchStatus(MatchStatus.MATCHED);
        return l;
    }

    private void withListings(CompetitorListing... rows) {
        when(products.existsById(any())).thenReturn(true);
        when(listings.findByProductIdOrderByCreatedAtDesc(any())).thenReturn(List.of(rows));
    }

    @Test
    @DisplayName("marketplaces with no sales data are not counted as sources, and nothing is estimated")
    void noDataIsUnavailableNotZero() {
        withListings(matched("AMAZON", "B0DGHMNQ5Z"), matched("EBAY", "v1|123|0"));
        when(marketplaces.getSales(any(), anyString())).thenAnswer(i -> SalesMetrics.builder()
                .marketplace(i.getArgument(0))
                .classification("UNAVAILABLE")
                .note(i.getArgument(0) == Marketplace.AMAZON
                        ? "Amazon SP-API does not provide competitor unit sales"
                        : "eBay sales history requires the restricted Marketplace Insights API")
                .build());

        SalesResponse response = service.getSales(7L);

        // Before: ESTIMATED, 0 units, 0 revenue, 2 sources — a zero that reads
        // as "we measured no sales" rather than "nobody reports sales".
        assertThat(response.getEstimatedClassification()).isEqualTo("UNAVAILABLE");
        assertThat(response.getEstimatedUnits()).isNull();
        assertThat(response.getEstimatedRevenue()).isNull();
        assertThat(response.getEstimateSources()).isZero();
        assertThat(response.getValueStatus()).containsEntry("estimatedUnits", "UNAVAILABLE");
        // And it says why, naming each marketplace's reason.
        assertThat(response.getEstimatedNote())
                .contains("Checked 2 matched listing(s)")
                .contains("SP-API does not provide competitor unit sales")
                .contains("Marketplace Insights");
    }

    @Test
    @DisplayName("a marketplace that does report sales is counted, and the figures add up")
    void realFiguresAreEstimated() {
        withListings(matched("AMAZON", "B0DGHMNQ5Z"), matched("EBAY", "v1|123|0"));
        when(marketplaces.getSales(any(), anyString())).thenAnswer(i -> {
            if (i.getArgument(0) == Marketplace.AMAZON) {
                return SalesMetrics.builder().marketplace(Marketplace.AMAZON)
                        .classification("UNAVAILABLE").note("no data").build();
            }
            return SalesMetrics.builder().marketplace(Marketplace.EBAY)
                    .estimatedUnits(40).estimatedRevenue(new BigDecimal("1200.00"))
                    .currency("USD").classification("ESTIMATED").build();
        });

        SalesResponse response = service.getSales(7L);

        assertThat(response.getEstimatedClassification()).isEqualTo("ESTIMATED");
        assertThat(response.getEstimatedUnits()).isEqualTo(40);
        assertThat(response.getEstimatedRevenue()).isEqualByComparingTo("1200.00");
        assertThat(response.getCurrency()).isEqualTo("USD");
        assertThat(response.getEstimateSources()).isEqualTo(1);   // only the one that had figures
    }

    @Test
    @DisplayName("with no matched listings at all, it says so plainly")
    void noMatchedListings() {
        withListings();

        SalesResponse response = service.getSales(7L);

        assertThat(response.getEstimatedClassification()).isEqualTo("UNAVAILABLE");
        assertThat(response.getEstimatedNote()).contains("No matched competitor listings");
    }

    @Test
    @DisplayName("own sales stay unavailable until a seller feed is connected")
    void ownSalesUnavailable() {
        withListings(matched("AMAZON", "B0DGHMNQ5Z"));
        when(marketplaces.getSales(any(), anyString())).thenReturn(SalesMetrics.builder()
                .marketplace(Marketplace.AMAZON).classification("UNAVAILABLE").build());

        SalesResponse response = service.getSales(7L);

        assertThat(response.getOwnedClassification()).isEqualTo("UNAVAILABLE");
        assertThat(response.getOwnedUnits()).isNull();
        assertThat(response.getOwnedNote()).contains("seller feed");
    }
}
