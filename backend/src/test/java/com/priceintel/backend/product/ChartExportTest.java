package com.priceintel.backend.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.priceintel.backend.dto.response.DashboardCharts;
import com.priceintel.backend.dto.response.DashboardSummaryItem;
import com.priceintel.backend.dto.response.PriceHistoryPoint;
import com.priceintel.backend.entity.ListingPriceSnapshot;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.service.impl.ChartExportService;
import com.priceintel.backend.service.impl.DashboardService;
import com.priceintel.backend.service.impl.SearchPipelineService;

/**
 * The download button behind each chart.
 *
 * <p>What is exported is the figures the chart was drawn from, so the file can
 * be opened in a spreadsheet and checked — which means an empty month has to
 * stay empty, not become a zero.</p>
 */
class ChartExportTest {

    private final DashboardService dashboard = mock(DashboardService.class);
    private final SearchPipelineService pipeline = mock(SearchPipelineService.class);
    private final ChartExportService service = new ChartExportService(dashboard, pipeline);

    private static String text(byte[] csv) {
        return new String(csv, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("the file opens as UTF-8 in Excel, accents intact")
    void hasByteOrderMark() {
        when(dashboard.charts()).thenReturn(DashboardCharts.builder()
                .categoryDistribution(DashboardCharts.ValueSeries.builder()
                        .labels(List.of("Café equipment")).values(List.of(new BigDecimal("3"))).build())
                .build());

        byte[] csv = service.dashboardChart(ChartExportService.Chart.CATEGORY_DISTRIBUTION);

        assertThat(Arrays.copyOf(csv, 3)).containsExactly((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        assertThat(text(csv)).contains("Café equipment,3");
    }

    @Test
    @DisplayName("a two-series chart exports both lines, and a month with no data stays blank")
    void priceTrendKeepsGaps() {
        when(dashboard.charts()).thenReturn(DashboardCharts.builder()
                .priceTrend(DashboardCharts.PriceTrend.builder()
                        .labels(List.of("Jul", "Aug"))
                        .ours(Arrays.asList(null, new BigDecimal("259.04")))
                        .market(Arrays.asList(null, new BigDecimal("689.75")))
                        .build())
                .build());

        String csv = text(service.dashboardChart(ChartExportService.Chart.PRICE_TREND));

        assertThat(csv).contains("Month,Our average price,Market average price");
        // Not "Jul,0,0" — nobody sold at nothing in July; there was no data.
        assertThat(csv).contains("Jul,,");
        assertThat(csv).contains("Aug,259.04,689.75");
    }

    @Test
    @DisplayName("tile exports leave the change column blank when there is nothing to compare with")
    void summaryBlankDelta() {
        when(dashboard.summary()).thenReturn(List.of(
                DashboardSummaryItem.builder().key("totalProducts").label("Total Products")
                        .value(new BigDecimal("26")).delta(8.3).build(),
                DashboardSummaryItem.builder().key("activeMonitors").label("Active Monitors")
                        .value(new BigDecimal("5")).build()));

        String csv = text(service.dashboardSummary());

        assertThat(csv).contains("Total Products,26,,8.3");
        assertThat(csv).contains("Active Monitors,5,,");
    }

    @Test
    @DisplayName("a listing's price history exports every observation with its own timestamp")
    void listingPriceHistory() {
        ListingPriceSnapshot point = new ListingPriceSnapshot();
        point.setObservedAt(Instant.parse("2026-09-28T06:15:00Z"));
        point.setMarketplace("EBAY");
        point.setStorefront("US");
        point.setMarketplaceItemId("wes-531384");
        point.setItemPrice(new BigDecimal("22.99"));
        point.setShipping(new BigDecimal("2.51"));
        point.setLandedPrice(new BigDecimal("25.50"));
        point.setCurrency("USD");
        when(pipeline.priceHistory(eq(42L), any(), any())).thenReturn(List.of(point));

        String csv = text(service.listingPriceHistory(42L, null, null));

        assertThat(csv).contains("Observed at,Marketplace,Storefront,Item id,"
                + "Item price,Shipping,Landed price,Currency");
        assertThat(csv).contains("2026-09-28T06:15:00Z,EBAY,US,wes-531384,22.99,2.51,25.50,USD");
    }

    @Test
    @DisplayName("a product's market history exports the whole spread, not just one line")
    void productPriceHistory() {
        PriceHistoryPoint p = PriceHistoryPoint.builder()
                .period(LocalDate.of(2026, 9, 28))
                .lowest(new BigDecimal("6.99")).median(new BigDecimal("28.00"))
                .average(new BigDecimal("31.83")).highest(new BigDecimal("241.99"))
                .ourPrice(new BigDecimal("22.99")).competitorCount(34).build();
        when(pipeline.priceHistoryRollup(eq(96L), any(), any(), anyString())).thenReturn(List.of(p));

        String csv = text(service.productPriceHistory(96L, null, null, "day"));

        assertThat(csv).contains("Period,Lowest,Median,Average,Highest,Our price,Competitors observed");
        assertThat(csv).contains("2026-09-28,6.99,28.00,31.83,241.99,22.99,34");
    }

    @Test
    @DisplayName("an unknown chart name says which names work")
    void unknownChart() {
        assertThatThrownBy(() -> ChartExportService.Chart.of("revenue"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("price-trend")
                .hasMessageContaining("search-activity");
    }

    @Test
    @DisplayName("the filename says what it is and when it was taken")
    void filename() {
        assertThat(service.dashboardFilename(ChartExportService.Chart.TOP_COMPETITORS))
                .isEqualTo("dashboard-top-competitors-" + LocalDate.now() + ".csv");
    }
}
