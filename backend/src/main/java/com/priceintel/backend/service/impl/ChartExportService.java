package com.priceintel.backend.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.priceintel.backend.dto.response.DashboardCharts;
import com.priceintel.backend.dto.response.DashboardSummaryItem;
import com.priceintel.backend.dto.response.PriceHistoryPoint;
import com.priceintel.backend.entity.ListingPriceSnapshot;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.utils.CsvExport;

import lombok.RequiredArgsConstructor;

/**
 * The download button behind each chart.
 *
 * <p>Exports the figures the chart was drawn from, not a picture of it, so the
 * file can be opened in a spreadsheet and checked, filtered or charted again.
 * Each export reads the same data the screen read, through the same
 * company-scoped services, so a download can never show more than the screen.</p>
 */
@Service
@RequiredArgsConstructor
public class ChartExportService {

    private final DashboardService dashboardService;
    private final SearchPipelineService searchPipeline;

    /** Which charts can be downloaded, named as the URL spells them. */
    public enum Chart {
        PRICE_TREND("price-trend"),
        MARKETPLACE_COMPARISON("marketplace-comparison"),
        PROFIT_ANALYSIS("profit-analysis"),
        TOP_COMPETITORS("top-competitors"),
        CATEGORY_DISTRIBUTION("category-distribution"),
        SEARCH_ACTIVITY("search-activity");

        private final String slug;

        Chart(String slug) {
            this.slug = slug;
        }

        public String slug() {
            return slug;
        }

        public static Chart of(String slug) {
            for (Chart c : values()) {
                if (c.slug.equalsIgnoreCase(slug) || c.name().equalsIgnoreCase(slug)) {
                    return c;
                }
            }
            throw new BadRequestException("Unknown chart: " + slug + ". Use one of: "
                    + java.util.Arrays.stream(values()).map(Chart::slug).collect(java.util.stream.Collectors.joining(", ")));
        }
    }

    // ---------- dashboard ----------

    @Transactional(readOnly = true)
    public byte[] dashboardSummary() {
        List<DashboardSummaryItem> tiles = dashboardService.summary();
        return CsvExport.write(new String[] {"Metric", "Value", "Unit", "Change vs previous period %"},
                printer -> {
                    for (DashboardSummaryItem t : tiles) {
                        printer.printRecord(t.getLabel(), t.getValue(),
                                t.getSuffix() != null ? t.getSuffix() : t.getPrefix(),
                                // Blank, not zero: no earlier period to compare with.
                                t.getDelta() == null ? "" : t.getDelta());
                    }
                });
    }

    @Transactional(readOnly = true)
    public byte[] dashboardChart(Chart chart) {
        DashboardCharts charts = dashboardService.charts();
        return switch (chart) {
            case PRICE_TREND -> twoSeries("Month", "Our average price", "Market average price",
                    charts.getPriceTrend() == null ? null : charts.getPriceTrend().getLabels(),
                    charts.getPriceTrend() == null ? null : charts.getPriceTrend().getOurs(),
                    charts.getPriceTrend() == null ? null : charts.getPriceTrend().getMarket());
            case PROFIT_ANALYSIS -> twoSeries("Month", "Gross profit per unit", "Net profit per unit",
                    charts.getProfitAnalysis() == null ? null : charts.getProfitAnalysis().getLabels(),
                    charts.getProfitAnalysis() == null ? null : charts.getProfitAnalysis().getGross(),
                    charts.getProfitAnalysis() == null ? null : charts.getProfitAnalysis().getNet());
            case MARKETPLACE_COMPARISON -> oneSeries("Marketplace", "Listings",
                    charts.getMarketplaceComparison());
            case TOP_COMPETITORS -> oneSeries("Seller", "Listings", charts.getTopCompetitors());
            case CATEGORY_DISTRIBUTION -> oneSeries("Category", "Products",
                    charts.getCategoryDistribution());
            case SEARCH_ACTIVITY -> oneSeries("Day", "Searches", charts.getSearchActivity());
        };
    }

    private byte[] oneSeries(String labelHeader, String valueHeader, DashboardCharts.ValueSeries series) {
        List<String> labels = series == null ? List.of() : series.getLabels();
        List<BigDecimal> values = series == null ? List.of() : series.getValues();
        return CsvExport.write(new String[] {labelHeader, valueHeader}, printer -> {
            for (int i = 0; i < labels.size(); i++) {
                printer.printRecord(labels.get(i), value(values, i));
            }
        });
    }

    private byte[] twoSeries(String labelHeader, String firstHeader, String secondHeader,
                             List<String> labels, List<BigDecimal> first, List<BigDecimal> second) {
        List<String> safeLabels = labels == null ? List.of() : labels;
        return CsvExport.write(new String[] {labelHeader, firstHeader, secondHeader}, printer -> {
            for (int i = 0; i < safeLabels.size(); i++) {
                // A month with no data stays empty rather than becoming 0 — a
                // zero would read as "we sold at nothing that month".
                printer.printRecord(safeLabels.get(i), value(first, i), value(second, i));
            }
        });
    }

    private Object value(List<BigDecimal> values, int i) {
        if (values == null || i >= values.size() || values.get(i) == null) {
            return "";
        }
        return values.get(i);
    }

    // ---------- price history ----------

    /** One listing's price over time — the "Price over time" chart on a product. */
    @Transactional(readOnly = true)
    public byte[] listingPriceHistory(Long listingId, Instant from, Instant to) {
        List<ListingPriceSnapshot> points = searchPipeline.priceHistory(listingId, from, to);
        return CsvExport.write(
                new String[] {"Observed at", "Marketplace", "Storefront", "Item id",
                        "Item price", "Shipping", "Landed price", "Currency"},
                printer -> {
                    for (ListingPriceSnapshot s : points) {
                        printer.printRecord(s.getObservedAt(), s.getMarketplace(), s.getStorefront(),
                                s.getMarketplaceItemId(), s.getItemPrice(), s.getShipping(),
                                s.getLandedPrice(), s.getCurrency());
                    }
                });
    }

    /** A product's market over time — lowest, median, average and highest per day. */
    @Transactional(readOnly = true)
    public byte[] productPriceHistory(Long productId, Instant from, Instant to, String bucket) {
        List<PriceHistoryPoint> points = searchPipeline.priceHistoryRollup(productId, from, to, bucket);
        return CsvExport.write(
                new String[] {"Period", "Lowest", "Median", "Average", "Highest",
                        "Our price", "Competitors observed"},
                printer -> {
                    for (PriceHistoryPoint p : points) {
                        printer.printRecord(p.getPeriod(), p.getLowest(), p.getMedian(), p.getAverage(),
                                p.getHighest(), p.getOurPrice(), p.getCompetitorCount());
                    }
                });
    }

    /** "price-trend" → "dashboard-price-trend-2026-09-28.csv". */
    public String dashboardFilename(Chart chart) {
        return CsvExport.filename("dashboard-" + chart.slug().toLowerCase(Locale.ROOT));
    }
}
