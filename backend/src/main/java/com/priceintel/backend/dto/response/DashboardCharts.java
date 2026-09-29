package com.priceintel.backend.dto.response;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The six Dashboard charts, each as labels plus one or more series — the shape
 * the chart component already reads.
 *
 * <p>A null inside a series means "no data for that period", which the chart
 * draws as a gap. That is deliberate: a zero would claim the average price was
 * nothing that month.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardCharts {

    /** Our selling price against the market, month by month. */
    private PriceTrend priceTrend;
    /** How many competitor listings come from each marketplace. */
    private ValueSeries marketplaceComparison;
    /** Gross and net profit per unit, month by month. */
    private ProfitSeries profitAnalysis;
    /** The sellers appearing most often across the catalogue. */
    private ValueSeries topCompetitors;
    /** Products per category. */
    private ValueSeries categoryDistribution;
    /** Searches run per day, over the last week. */
    private ValueSeries searchActivity;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceTrend {
        private List<String> labels;
        private List<BigDecimal> ours;
        private List<BigDecimal> market;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProfitSeries {
        private List<String> labels;
        private List<BigDecimal> gross;
        private List<BigDecimal> net;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValueSeries {
        private List<String> labels;
        private List<BigDecimal> values;
    }
}
