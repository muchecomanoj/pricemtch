package com.priceintel.backend.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;

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
 * A subscription plan the SUPER_ADMIN manages and assigns to tenants. Also
 * surfaced publicly on the landing page pricing.
 */
@Entity
@Table(name = "subscription_plans", uniqueConstraints = {
        @UniqueConstraint(name = "uk_subscription_plans_code", columnNames = "code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPlan extends BaseEntity {

    @Column(nullable = false, length = 50)
    private String code; // e.g. FREE, BASIC, PRO, ENTERPRISE

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Builder.Default
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    @Column(length = 10)
    private String currency;

    @Builder.Default
    @Column(name = "billing_cycle_days", nullable = false)
    private int billingCycleDays = 30;

    @Builder.Default
    @Column(name = "max_users", nullable = false)
    private int maxUsers = 5;

    /**
     * Fastest competitor-check cadence this plan allows, in minutes.
     *
     * <p>A third axis alongside seats and features, and it does real work beyond
     * pricing: every scheduled check spends marketplace API quota that is shared
     * across all tenants, so a floor per tier protects that quota as much as it
     * segments the product. Default is daily.</p>
     */
    @Builder.Default
    @Column(name = "min_interval_minutes", nullable = false)
    private int minIntervalMinutes = 1440;

    /** Free-trial length applied when a client subscribes. */
    @Builder.Default
    @Column(name = "trial_days", nullable = false)
    private int trialDays = 14;

    /**
     * Optional explicit yearly price. When null, the yearly price is derived
     * from the monthly price and {@link #yearlyDiscountPercent}.
     */
    @Column(name = "yearly_price", precision = 12, scale = 2)
    private BigDecimal yearlyPrice;

    /**
     * Discount (%) applied to the 12-month total when billed yearly, set by the
     * super admin. Used only when {@link #yearlyPrice} is not set explicitly.
     * Null is treated as the default 20%.
     */
    @Builder.Default
    @Column(name = "yearly_discount_percent")
    private Integer yearlyDiscountPercent = 20;

    /** Comma-separated feature list for display. */
    @Column(length = 1000)
    private String features;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    /** Default yearly discount when none is configured on the plan. */
    public static final int DEFAULT_YEARLY_DISCOUNT_PERCENT = 20;

    /** The effective discount %, falling back to the default when unset. */
    public int effectiveYearlyDiscountPercent() {
        return yearlyDiscountPercent != null ? yearlyDiscountPercent : DEFAULT_YEARLY_DISCOUNT_PERCENT;
    }

    /**
     * The price charged for a yearly subscription: the explicit {@code yearlyPrice}
     * if set, otherwise {@code monthly x 12} reduced by the yearly discount.
     */
    public BigDecimal computeYearlyPrice() {
        if (yearlyPrice != null) {
            return yearlyPrice;
        }
        BigDecimal factor = BigDecimal.valueOf(100L - effectiveYearlyDiscountPercent())
                .divide(BigDecimal.valueOf(100));
        return price.multiply(BigDecimal.valueOf(12)).multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }
}
