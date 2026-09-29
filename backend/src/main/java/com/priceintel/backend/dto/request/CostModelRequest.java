package com.priceintel.backend.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The tenant default cost model (Cost Management page). Mirrors
 * {@link CostProfileRequest} so it can serve as a per-product fallback.
 * All fields optional; omitted = 0.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostModelRequest {

    @Size(max = 10)
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

    /** PASS_THROUGH, INCLUSIVE or NONE. Omitted leaves it unchanged; a new model defaults to PASS_THROUGH. */
    private com.priceintel.backend.constants.TaxTreatment taxTreatment;
}
