package com.priceintel.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One point on the product-level Historical Prices chart: competitor price
 * stats for a time bucket, plus our current price as a reference line.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceHistoryPoint {
    private LocalDate period;        // bucket start (day or week)
    private BigDecimal lowest;
    private BigDecimal highest;
    private BigDecimal median;
    private BigDecimal average;
    private BigDecimal ourPrice;     // reference (current selling price)
    private int competitorCount;     // listings observed in this bucket

    /**
     * How far prices in this bucket spread, as a percentage of the average
     * (FR-PRICE-003).
     *
     * <p>The coefficient of variation — standard deviation over the mean —
     * rather than the standard deviation itself, so a £400 monitor and a £4
     * cable can be compared. A £10 spread is noise on one and chaos on the
     * other; expressing it as a percentage makes the two answerable by the
     * same threshold.</p>
     *
     * <p>Null with fewer than two prices: a single observation has no spread,
     * and reporting 0 would claim a stability nothing was measured.</p>
     */
    private BigDecimal volatilityPct;

    /** Highest minus lowest in this bucket — the spread in currency. */
    private BigDecimal priceRange;
}
