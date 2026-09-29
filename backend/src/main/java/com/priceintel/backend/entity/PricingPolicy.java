package com.priceintel.backend.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The bounds a recommended price must stay inside (FR-REC-002).
 *
 * <p>Stored rather than passed with each request. A bound supplied by the caller
 * protects a human who remembers to supply it; automation has nobody to
 * remember, which is why repricing could not safely be switched on without
 * this.</p>
 *
 * <p>Scoped, and resolved most-specific-first:</p>
 * <pre>
 *   product + channel  →  product  →  tenant + channel  →  tenant
 * </pre>
 */
@Entity
@Table(name = "pricing_policies",
        indexes = @Index(name = "idx_pricing_policy_tenant", columnList = "tenant_id, active"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PricingPolicy extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /** Null applies to every product of this tenant. */
    @Column(name = "product_id")
    private Long productId;

    /** Null applies to every channel. */
    @Column(length = 20)
    private String marketplace;

    /** Never recommend below this, whatever the market is doing. */
    @Column(name = "floor_price", precision = 12, scale = 2)
    private BigDecimal floorPrice;

    /**
     * A floor expressed as margin rather than a fixed amount.
     *
     * <p>Held alongside {@link #floorPrice} because neither replaces the other:
     * a fixed floor cannot know today's costs, and a margin floor cannot say
     * "never below what we paid". Whichever is higher at the moment of
     * calculation wins.</p>
     */
    @Column(name = "floor_margin_pct", precision = 9, scale = 4)
    private BigDecimal floorMarginPct;

    @Column(name = "ceiling_price", precision = 12, scale = 2)
    private BigDecimal ceilingPrice;

    /**
     * How far a single cycle may move the price.
     *
     * <p>A floor stops one bad recommendation. This stops a <em>sequence</em> of
     * individually reasonable ones walking the price down over days — every step
     * legal, the destination nowhere near intended.</p>
     */
    @Column(name = "max_change_pct", precision = 9, scale = 4)
    private BigDecimal maxChangePct;

    @Column(name = "max_change_amount", precision = 12, scale = 2)
    private BigDecimal maxChangeAmount;

    /**
     * Minimum hours between two published changes to the same product.
     *
     * <p>Stops the system reacting to its own last move before the market has
     * had a chance to respond to it.</p>
     */
    @Column(name = "cooldown_hours")
    private Integer cooldownHours;

    /** Off unless deliberately enabled, and refused where no bounds exist. */
    @Column(name = "auto_publish", nullable = false)
    @Builder.Default
    private boolean autoPublish = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /** True when this policy constrains anything at all. */
    public boolean hasAnyBound() {
        return floorPrice != null || floorMarginPct != null || ceilingPrice != null
                || maxChangePct != null || maxChangeAmount != null;
    }

    /** How specific this policy is — higher wins when several match. */
    public int specificity() {
        return (productId != null ? 2 : 0) + (marketplace != null ? 1 : 0);
    }
}
