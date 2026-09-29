package com.priceintel.backend.entity;

import java.math.BigDecimal;

import com.priceintel.backend.constants.AlertCondition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A monitoring rule for a product (FR-ALERT-001). e.g. "notify me if a
 * competitor price drops more than 5%".
 */
@Entity
@Table(name = "alert_rules",
        indexes = @Index(name = "idx_alert_rule_product", columnList = "product_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertRule extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AlertCondition condition;

    /** "ABSOLUTE" or "PERCENT". */
    @Builder.Default
    @Column(name = "threshold_type", length = 10)
    private String thresholdType = "PERCENT";

    @Column(name = "threshold_value", precision = 12, scale = 2)
    private BigDecimal thresholdValue;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Column(length = 300)
    private String note;

    /**
     * Where this rule's alerts go, comma-separated: IN_APP, EMAIL, SLACK, TEAMS.
     *
     * <p>Per rule rather than per tenant, so a margin breach can page the team
     * in Slack while a routine competitor price move stays in the bell. Null
     * means IN_APP, which is how every existing rule behaved.</p>
     */
    @Column(length = 100)
    private String channels;

    // ---- evaluation state (used by the alert engine) ----

    /** Baseline value from the last evaluation (e.g. previous median price). */
    @Column(name = "last_observed_value", precision = 12, scale = 2)
    private java.math.BigDecimal lastObservedValue;

    /** Baseline competitor count (for NEW_SELLER). */
    @Column(name = "last_listing_count")
    private Integer lastListingCount;

    /** When this rule last fired a notification (for cooldown + history). */
    @Column(name = "last_fired_at")
    private java.time.Instant lastFiredAt;

    @Builder.Default
    @Column(name = "trigger_count", nullable = false)
    private int triggerCount = 0;
}
