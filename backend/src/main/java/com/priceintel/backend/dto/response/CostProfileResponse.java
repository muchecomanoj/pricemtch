package com.priceintel.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A product's stored cost profile. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostProfileResponse {
    private Long productId;
    private String currency;
    private BigDecimal cogs;
    private BigDecimal inboundFreight;
    private BigDecimal duty;
    private BigDecimal prep;
    private BigDecimal fulfilmentFee;
    private BigDecimal storage;
    private BigDecimal advertising;
    private BigDecimal overheadAllocation;
    private BigDecimal outboundShipping;
    private BigDecimal referralRatePct;
    private BigDecimal paymentFeeRatePct;
    private BigDecimal returnsAllowancePct;
    private BigDecimal taxRatePct;

    /** PASS_THROUGH, INCLUSIVE or NONE — whether the rate above is added to the price or inside it. */
    private String taxTreatment;
    private LocalDate validFrom;
    private String note;
    private boolean configured;   // false when no cost profile has been saved yet
}
