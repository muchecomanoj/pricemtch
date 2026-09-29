package com.priceintel.backend.entity;

import java.time.Instant;

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
 * A standing instruction to re-check a product's competitors on a schedule.
 *
 * <p>Discovery was previously manual and one product at a time, so in practice
 * most of a catalogue was never re-checked and the screens that read from it —
 * price history, trends, alerts, recommendations — had little to work with. A
 * monitor turns that click into a cadence.</p>
 */
@Entity
@Table(name = "monitors",
        uniqueConstraints = @UniqueConstraint(name = "uk_monitor_product",
                columnNames = "product_id"),
        indexes = {
                @Index(name = "idx_monitor_due", columnList = "enabled, next_run_at"),
                @Index(name = "idx_monitor_tenant", columnList = "tenant_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Monitor extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /** One monitor per product — a second would just duplicate API spend. */
    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** Marketplaces to search, comma-separated. Blank means all enabled ones. */
    @Column(length = 100)
    private String markets;

    @Builder.Default
    @Column(name = "max_results")
    private Integer maxResults = 5;

    /**
     * How often to re-check. Enforced against the tenant plan's floor, because
     * every run spends marketplace quota that is shared across all clients.
     */
    @Builder.Default
    @Column(name = "interval_minutes", nullable = false)
    private Integer intervalMinutes = 1440;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

    /** When this monitor next becomes due. Drives the scheduler's query. */
    @Column(name = "next_run_at")
    private Instant nextRunAt;

    @Column(name = "last_result_count")
    private Integer lastResultCount;

    /**
     * Outcome of the last run — COMPLETED or FAILED. Surfaced so a monitor that
     * has quietly been failing is visible, rather than merely silent.
     */
    @Column(name = "last_status", length = 20)
    private String lastStatus;

    /** Why the last run failed, or what it noted (e.g. served from cache). */
    @Column(name = "last_message", length = 500)
    private String lastMessage;
}
