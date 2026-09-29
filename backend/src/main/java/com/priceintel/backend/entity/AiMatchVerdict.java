package com.priceintel.backend.entity;

import com.priceintel.backend.constants.AiProvider;

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
 * One AI judgement on whether a candidate listing is the same product
 * (FR-MATCH-003).
 *
 * <p>Advisory. The verdict sits beside the deterministic score and the reviewer
 * decides; nothing here changes {@code match_status} on its own. A model that
 * quietly accepted or rejected candidates would leave no way to tell a good run
 * from a bad one — and no way back once a few hundred had been decided.</p>
 */
@Entity
@Table(name = "ai_match_verdicts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiMatchVerdict extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(length = 20)
    private String marketplace;

    @Column(name = "marketplace_item_id", length = 100)
    private String marketplaceItemId;

    /** MATCH, NOT_MATCH or UNCERTAIN. */
    @Column(nullable = false, length = 20)
    private String decision;

    /** The model's own confidence, 0-100 — distinct from the rules score. */
    private Integer score;

    @Column(length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiProvider provider;

    @Column(length = 60)
    private String model;

    /**
     * The prompt this verdict came from.
     *
     * <p>Part of the key rather than a note: changing the prompt changes the
     * answers, and a cache that ignored it would keep serving judgements the
     * current prompt never made.</p>
     */
    @Column(name = "prompt_version", length = 30)
    private String promptVersion;

    @Column(name = "total_tokens")
    private Integer totalTokens;
}
