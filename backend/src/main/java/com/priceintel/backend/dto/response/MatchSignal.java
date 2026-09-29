package com.priceintel.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One reason (signal) contributing to a match score, shown as a chip on the
 * Match Review card. {@code positive=false} signals are warnings (e.g. a used
 * condition or a pack-size mismatch) and are rendered as red/amber badges.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchSignal {
    /** Stable machine code, e.g. IDENTIFIER_MATCH, BRAND_MODEL, TITLE_SIMILARITY, CONDITION, PACK_SIZE. */
    private String code;
    /** Human label shown on the chip, e.g. "ASIN exact match", "Title similarity 0.88". */
    private String label;
    /** true = supports the match (green), false = warning against it (red/amber). */
    private boolean positive;
}
