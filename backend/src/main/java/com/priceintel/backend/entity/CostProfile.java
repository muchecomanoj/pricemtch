package com.priceintel.backend.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cost inputs for a product (FR-COST-001), used by the deterministic
 * profitability engine. Flat amounts are per-unit; rate fields are percentages
 * of the selling price.
 *
 * <p>A product has one profile <em>per effective date</em>, not one outright.
 * Costs change — a supplier raises a price, freight moves — and overwriting them
 * silently restated every historical margin the moment somebody typed a new
 * number. Each calculation asks for the profile that was in force on the date it
 * cares about.</p>
 */
@Entity
@Table(name = "cost_profiles", uniqueConstraints = {
        @UniqueConstraint(name = "uq_cost_profile_product_from",
                columnNames = {"product_id", "valid_from"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CostProfile extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Builder.Default
    @Column(length = 10)
    private String currency = "USD";

    // ---- per-unit flat costs ----
    @Builder.Default @Column(precision = 12, scale = 2) private BigDecimal cogs = BigDecimal.ZERO;
    @Builder.Default @Column(name = "inbound_freight", precision = 12, scale = 2) private BigDecimal inboundFreight = BigDecimal.ZERO;
    @Builder.Default @Column(precision = 12, scale = 2) private BigDecimal duty = BigDecimal.ZERO;
    @Builder.Default @Column(precision = 12, scale = 2) private BigDecimal prep = BigDecimal.ZERO;
    @Builder.Default @Column(name = "fulfilment_fee", precision = 12, scale = 2) private BigDecimal fulfilmentFee = BigDecimal.ZERO;
    @Builder.Default @Column(precision = 12, scale = 2) private BigDecimal storage = BigDecimal.ZERO;
    @Builder.Default @Column(precision = 12, scale = 2) private BigDecimal advertising = BigDecimal.ZERO;
    @Builder.Default @Column(name = "overhead_allocation", precision = 12, scale = 2) private BigDecimal overheadAllocation = BigDecimal.ZERO;
    @Builder.Default @Column(name = "outbound_shipping", precision = 12, scale = 2) private BigDecimal outboundShipping = BigDecimal.ZERO;

    // ---- percentage-of-price rates ----
    @Builder.Default @Column(name = "referral_rate_pct", precision = 6, scale = 3) private BigDecimal referralRatePct = BigDecimal.ZERO;
    @Builder.Default @Column(name = "payment_fee_rate_pct", precision = 6, scale = 3) private BigDecimal paymentFeeRatePct = BigDecimal.ZERO;
    @Builder.Default @Column(name = "returns_allowance_pct", precision = 6, scale = 3) private BigDecimal returnsAllowancePct = BigDecimal.ZERO;
    @Builder.Default @Column(name = "tax_rate_pct", precision = 6, scale = 3) private BigDecimal taxRatePct = BigDecimal.ZERO;

    /**
     * Whether {@code taxRatePct} is added to the price or already inside it.
     *
     * <p>Decides whether the seller receives the listed price or that price less
     * the tax — a difference of the whole VAT rate in every margin, break-even
     * and recommendation for the product.</p>
     */
    @Builder.Default
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "tax_treatment", nullable = false, length = 20)
    private com.priceintel.backend.constants.TaxTreatment taxTreatment =
            com.priceintel.backend.constants.TaxTreatment.PASS_THROUGH;

    /**
     * The date these costs took effect.
     *
     * <p>Part of the profile's identity, not a note about it. {@code 1900-01-01}
     * is the sentinel for "since before anything recorded here", used for
     * profiles that predate dated costing — a real date rather than null,
     * because "unknown start" and "always" are different claims and only one of
     * them can be indexed.</p>
     */
    @Builder.Default
    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom = LocalDate.of(1900, 1, 1);

    @Column(length = 500)
    private String note;
}
