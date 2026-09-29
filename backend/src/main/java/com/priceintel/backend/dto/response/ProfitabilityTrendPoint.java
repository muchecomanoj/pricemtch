package com.priceintel.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One point on the Net Profit trend chart. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfitabilityTrendPoint {
    private LocalDate period;
    private BigDecimal netProfit;
    private BigDecimal marginPct;
    private BigDecimal roiPct;
    private BigDecimal sellingPrice;
}
