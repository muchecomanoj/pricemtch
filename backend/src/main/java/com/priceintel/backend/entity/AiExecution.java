package com.priceintel.backend.entity;

import com.priceintel.backend.constants.AiProvider;
import com.priceintel.backend.constants.MatchDecision;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Governance/audit record for every AI (or deterministic) execution: which
 * provider/model/prompt version ran, token usage, latency, and the outcome.
 */
@Entity
@Table(name = "ai_executions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiExecution extends BaseEntity {

    /** Owning tenant, so one client never reads another's AI audit trail. */
    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(nullable = false, length = 50)
    private String task; // e.g. PRODUCT_MATCH

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiProvider provider;

    @Column(length = 60)
    private String model;

    @Column(name = "prompt_version", length = 30)
    private String promptVersion;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "schema_status", length = 20)
    private String schemaStatus; // VALID / INVALID

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private MatchDecision decision;

    private Integer score;

    @Column(nullable = false)
    private boolean success;

    @Column(length = 500)
    private String message;
}
