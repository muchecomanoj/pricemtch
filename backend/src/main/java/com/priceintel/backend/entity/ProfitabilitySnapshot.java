package com.priceintel.backend.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A daily snapshot of a product's profitability, so the Profitability page can
 * chart a Net Profit trend over time. One row per product per day.
 */
@Entity
@Table(name = "profitability_snapshots",
        uniqueConstraints = @UniqueConstraint(name = "uk_profit_snapshot",
                columnNames = {"product_id", "snapshot_date"}),
        indexes = @Index(name = "idx_profit_snapshot_product", columnList = "product_id, snapshot_date"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfitabilitySnapshot extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "selling_price", precision = 12, scale = 2)
    private BigDecimal sellingPrice;

    @Column(name = "net_profit", precision = 12, scale = 2)
    private BigDecimal netProfit;

    @Column(name = "contribution_margin_pct", precision = 6, scale = 2)
    private BigDecimal contributionMarginPct;

    @Column(name = "roi_pct", precision = 8, scale = 2)
    private BigDecimal roiPct;

    @Column(name = "break_even_price", precision = 12, scale = 2)
    private BigDecimal breakEvenPrice;

    @Column(length = 10)
    private String currency;
}
