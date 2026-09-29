package com.priceintel.backend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A request to match a product against a candidate. Provide EITHER a
 * {@code candidateId} (another stored product) OR an inline {@code candidate}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchRequest {

    @NotNull(message = "productId is required")
    private Long productId;

    /** Match against another stored product (optional). */
    private Long candidateId;

    /** Match against inline candidate attributes (optional). */
    @Valid
    private MatchCandidate candidate;
}
