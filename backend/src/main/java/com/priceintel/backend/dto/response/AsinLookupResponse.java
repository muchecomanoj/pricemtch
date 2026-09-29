package com.priceintel.backend.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The result of an ASIN lookup, and whether the lookup actually happened.
 *
 * <p>An empty list is ambiguous in a way that misleads: "we searched and this
 * product is not on Amazon" and "the provider refused the request" produce the
 * same zero results, and only the first is a fact about the product. Told the
 * wrong one, a user goes off to fix an identifier that was never the problem.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsinLookupResponse {

    /** Candidates confirmed to exist on Amazon. Empty unless status is FOUND. */
    private List<AsinSuggestion> suggestions;

    /**
     * What happened:
     * <ul>
     *   <li>{@code FOUND} — at least one candidate verified</li>
     *   <li>{@code NONE} — searched, nothing matched, or nothing it proposed
     *       existed on Amazon</li>
     *   <li>{@code RATE_LIMITED} — the provider's quota; try again shortly</li>
     *   <li>{@code UNAVAILABLE} — the provider failed or is not configured</li>
     * </ul>
     */
    private String status;

    /** A sentence that can be shown to the user as-is. */
    private String message;

    /** True when trying again in a moment could plausibly succeed. */
    private boolean retryable;

    /** How many the model proposed before verification — 0 with a FOUND is impossible. */
    private int proposedCount;
}
