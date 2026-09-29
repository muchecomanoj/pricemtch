package com.priceintel.backend.dto.response;

import java.util.List;

import com.priceintel.backend.constants.AiProvider;
import com.priceintel.backend.constants.MatchDecision;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The structured result of a product match — the JSON returned to clients.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchResponse {

    private int score;                    // 0-100
    private MatchDecision decision;
    private String reason;
    private List<MatchConflict> conflicts;
    private List<String> missingEvidence;
    private List<String> matchedAttributes;
    private double confidence;            // 0.0-1.0

    // provenance / governance
    private AiProvider provider;
    private String model;
    private String promptVersion;
    private TokenUsage tokenUsage;
    private Long executionId;
}
