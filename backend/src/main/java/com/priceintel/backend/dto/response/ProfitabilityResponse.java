package com.priceintel.backend.dto.response;

import java.math.BigDecimal;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Deterministic profitability result (FR-PROFIT-001) for a product at a given
 * selling price. Every input is exposed so the calculation is auditable.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfitabilityResponse {
    private Long productId;
    private String currency;
    private boolean costProfileConfigured;   // false if no costs (product or tenant) → results are null
    private String costSource;               // "PRODUCT" | "TENANT_DEFAULT" | null (when not configured)

    private BigDecimal sellingPrice;         // the price used for the calc

    // fee breakdown (derived from rates × price)
    private BigDecimal referralFee;
    private BigDecimal paymentFee;
    private BigDecimal returnsAllowance;
    private BigDecimal tax;

    /**
     * PASS_THROUGH, INCLUSIVE or NONE — how {@link #tax} relates to
     * {@link #sellingPrice}.
     *
     * <p>Under PASS_THROUGH the tax is added at checkout and net revenue equals
     * the selling price. Under INCLUSIVE it is already inside the price and net
     * revenue is the price less the tax — a difference of the whole VAT rate in
     * every figure below. The screen should say which, because the two produce
     * very different margins from the same price.</p>
     */
    private String taxTreatment;

    private BigDecimal variableCost;         // all per-unit variable costs
    private BigDecimal netRevenue;
    private BigDecimal contributionProfit;
    private BigDecimal grossProfit;
    private BigDecimal netProfitEstimate;    // contribution − overhead

    private BigDecimal contributionMarginPct;
    private BigDecimal roiPct;
    private BigDecimal breakEvenPrice;
    private BigDecimal maxAllowableAcquisitionCost;

    private String formulaVersion;

    /**
     * Provenance of each figure above, keyed by field name (FR-REPORT-001).
     *
     * <p>These are deterministic arithmetic, so they read CALCULATED when the
     * product has its own cost profile. Where the tenant-wide default is used
     * instead they read ESTIMATED — the sums are exact, but the inputs are not
     * specific to this product, and a margin is only as particular as the costs
     * behind it. With no costs at all they are UNAVAILABLE rather than zero.</p>
     */
    private Map<String, String> valueStatus;
}
