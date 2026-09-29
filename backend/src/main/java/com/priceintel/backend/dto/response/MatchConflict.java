package com.priceintel.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A single conflicting attribute between the product and the candidate. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchConflict {
    private String field;
    private String productValue;
    private String candidateValue;
    private String severity; // HARD / SOFT
}
