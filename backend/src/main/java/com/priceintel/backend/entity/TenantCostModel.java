package com.priceintel.backend.entity;

import java.math.BigDecimal;

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
 * A tenant's default cost model (Cost Management page). One row per tenant.
 * Mirrors {@link CostProfile} field-for-field so it can serve as the fallback
 * when a product has no cost profile of its own. Flat amounts are per-unit;
 * rate fields are percentages of the selling price.
 */
@Entity
@Table(name = "tenant_cost_models",
        uniqueConstraints = @UniqueConstraint(name = "uk_cost_model_tenant", columnNames = "tenant_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantCostModel extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Builder.Default
    @Column(length = 10)
    private String currency = "USD";

    // ---- per-unit flat costs (mirror CostProfile) ----
    @Builder.Default @Column(precision = 12, scale = 2) private BigDecimal cogs = BigDecimal.ZERO;
    @Builder.Default @Column(name = "inbound_freight", precision = 12, scale = 2) private BigDecimal inboundFreight = BigDecimal.ZERO;
    @Builder.Default @Column(precision = 12, scale = 2) private BigDecimal duty = BigDecimal.ZERO;
    @Builder.Default @Column(precision = 12, scale = 2) private BigDecimal prep = BigDecimal.ZERO;
    @Builder.Default @Column(name = "fulfilment_fee", precision = 12, scale = 2) private BigDecimal fulfilmentFee = BigDecimal.ZERO;
    @Builder.Default @Column(precision = 12, scale = 2) private BigDecimal storage = BigDecimal.ZERO;
    @Builder.Default @Column(precision = 12, scale = 2) private BigDecimal advertising = BigDecimal.ZERO;
    @Builder.Default @Column(name = "overhead_allocation", precision = 12, scale = 2) private BigDecimal overheadAllocation = BigDecimal.ZERO;
    @Builder.Default @Column(name = "outbound_shipping", precision = 12, scale = 2) private BigDecimal outboundShipping = BigDecimal.ZERO;

    // ---- percentage-of-price rates (mirror CostProfile) ----
    @Builder.Default @Column(name = "referral_rate_pct", precision = 6, scale = 3) private BigDecimal referralRatePct = BigDecimal.ZERO;
    @Builder.Default @Column(name = "payment_fee_rate_pct", precision = 6, scale = 3) private BigDecimal paymentFeeRatePct = BigDecimal.ZERO;
    @Builder.Default @Column(name = "returns_allowance_pct", precision = 6, scale = 3) private BigDecimal returnsAllowancePct = BigDecimal.ZERO;
    @Builder.Default @Column(name = "tax_rate_pct", precision = 6, scale = 3) private BigDecimal taxRatePct = BigDecimal.ZERO;

    /** Whether the tax rate above is added to prices or already inside them. */
    @Builder.Default
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "tax_treatment", nullable = false, length = 20)
    private com.priceintel.backend.constants.TaxTreatment taxTreatment =
            com.priceintel.backend.constants.TaxTreatment.PASS_THROUGH;
}
