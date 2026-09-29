package com.priceintel.backend.dto.response;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostModelResponse {
    private Long tenantId;
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

    /** PASS_THROUGH, INCLUSIVE or NONE. */
    private String taxTreatment;

    /** True once the tenant has saved a model at least once. */
    private boolean configured;
}
