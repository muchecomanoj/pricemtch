package com.priceintel.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationResponse {
    private Long id;
    private Long productId;
    private String productTitle;
    /** The product's current selling price (ourPrice) — the "Current" column. */
    private BigDecimal currentPrice;
    private BigDecimal recommendedPrice;
    /** LOW / MEDIUM / HIGH — how big a change this is vs the current price. */
    private String riskLevel;
    private String currency;
    private BigDecimal expectedMarginPct;
    private BigDecimal breakEvenPrice;
    private BigDecimal competitorMedian;
    private BigDecimal confidence;
    private String rationale;
    private String status;
    private LocalDate expiresAt;
    private LocalDateTime createdAt;

    /**
     * What the engine produced before the pricing policy constrained it.
     *
     * <p>Equal to {@code recommendedPrice} when nothing capped it. Shown so a
     * capped price is visibly capped — otherwise a policy that is quietly
     * distorting every answer looks identical to one that never fires.</p>
     */
    private BigDecimal unboundedPrice;

    /** FLOOR_PRICE / FLOOR_MARGIN / CEILING / MAX_CHANGE, or null when uncapped. */
    private String boundApplied;

    /** The selling price this replaced, captured at publish. Null before publishing. */
    private BigDecimal previousPrice;

    private java.time.Instant publishedAt;

    /** Set once rolled back; non-null means the previous price has been restored. */
    private java.time.Instant rolledBackAt;

    /** True when this can be rolled back right now — saves the UI re-deriving the rule. */
    private boolean rollbackable;
}
