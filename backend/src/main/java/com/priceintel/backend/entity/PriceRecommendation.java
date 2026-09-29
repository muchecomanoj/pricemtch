package com.priceintel.backend.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.priceintel.backend.constants.RecommendationStatus;

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
 * A validated price recommendation (FR-REC-001). The numbers come from the
 * deterministic engine; the rationale explains them. Requires approval before
 * publishing (FR-REC-002).
 */
@Entity
@Table(name = "price_recommendations",
        indexes = @Index(name = "idx_recommendation_product", columnList = "product_id, status"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceRecommendation extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "recommended_price", precision = 12, scale = 2)
    private BigDecimal recommendedPrice;

    @Column(length = 10)
    private String currency;

    @Column(name = "expected_margin_pct", precision = 6, scale = 2)
    private BigDecimal expectedMarginPct;

    @Column(name = "break_even_price", precision = 12, scale = 2)
    private BigDecimal breakEvenPrice;

    @Column(name = "competitor_median", precision = 12, scale = 2)
    private BigDecimal competitorMedian;

    /** 0–100 confidence from data coverage/freshness. */
    @Column(precision = 5, scale = 1)
    private BigDecimal confidence;

    @Column(length = 800)
    private String rationale;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RecommendationStatus status = RecommendationStatus.DRAFT;

    @Column(name = "expires_at")
    private LocalDate expiresAt;

    /**
     * What the optimiser produced before the policy constrained it.
     *
     * <p>Kept because a capped recommendation and an uncapped one that landed on
     * the same number are otherwise indistinguishable — so nobody can tell
     * whether a policy is protecting them or quietly distorting every answer.</p>
     */
    @Column(name = "unbounded_price", precision = 12, scale = 2)
    private java.math.BigDecimal unboundedPrice;

    /** Which bound moved the price: FLOOR_PRICE, FLOOR_MARGIN, CEILING, MAX_CHANGE. */
    @Column(name = "bound_applied", length = 30)
    private String boundApplied;

    /**
     * The selling price this replaced, captured at publish.
     *
     * <p>Rollback needs somewhere to go back to, and asking the user to remember
     * what a price used to be is not a recovery plan.</p>
     */
    @Column(name = "previous_price", precision = 12, scale = 2)
    private java.math.BigDecimal previousPrice;

    @Column(name = "published_at")
    private java.time.Instant publishedAt;

    @Column(name = "rolled_back_at")
    private java.time.Instant rolledBackAt;
}
