package com.priceintel.backend.dto.request;

import com.priceintel.backend.constants.MatchStatus;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Accept/reject/mark a candidate competitor listing. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRequest {

    @NotNull(message = "decision is required (MATCHED, EQUIVALENT, or REJECTED)")
    private MatchStatus decision;
}
