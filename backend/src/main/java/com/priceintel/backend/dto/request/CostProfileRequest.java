package com.priceintel.backend.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Create/update a product's cost profile (FR-COST-001). All fields optional; omitted = 0. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostProfileRequest {

    private String currency;

    // per-unit flat costs
    private BigDecimal cogs;
    private BigDecimal inboundFreight;
    private BigDecimal duty;
    private BigDecimal prep;
    private BigDecimal fulfilmentFee;
    private BigDecimal storage;
    private BigDecimal advertising;
    private BigDecimal overheadAllocation;
    private BigDecimal outboundShipping;

    // percentage-of-price rates
    private BigDecimal referralRatePct;
    private BigDecimal paymentFeeRatePct;
    private BigDecimal returnsAllowancePct;
    private BigDecimal taxRatePct;

    /**
     * PASS_THROUGH (added at checkout — the US default), INCLUSIVE (already in
     * the price — VAT, GST) or NONE. Omitted leaves the existing treatment
     * unchanged, and a new profile defaults to PASS_THROUGH, which is what every
     * profile does today.
     */
    private com.priceintel.backend.constants.TaxTreatment taxTreatment;

    private LocalDate validFrom;
    private String note;
}
