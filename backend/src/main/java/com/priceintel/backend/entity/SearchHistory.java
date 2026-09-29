package com.priceintel.backend.entity;

import com.priceintel.backend.constants.SearchStage;

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
 * A record of one search request and its outcome (audit + analytics).
 * The searcher and timestamp come from the inherited audit fields.
 */
@Entity
@Table(name = "search_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchHistory extends BaseEntity {

    /** Owning tenant, so one client never reads another's search log. */
    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "query_text", nullable = false, length = 500)
    private String query;

    @Enumerated(EnumType.STRING)
    @Column(name = "matched_stage", nullable = false, length = 20)
    private SearchStage matchedStage;

    @Column(name = "match_count", nullable = false)
    private int matchCount;

    /** The full stage-by-stage trace, stored as JSON for later inspection. */
    @Column(name = "trace_json", length = 4000)
    private String traceJson;
}
