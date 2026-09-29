package com.priceintel.backend.dto.response;

import java.time.Instant;
import java.util.List;

import com.priceintel.backend.marketplace.model.SearchResultItem;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One search, its results, and an account of how they were reached
 * (FR-SRCH-003).
 *
 * <p>The trace is not diagnostics for developers. "No competitors found" is
 * ambiguous in a way that costs money: it can mean the product has none, that
 * the identifier was wrong, that the marketplace refused us, or that we never
 * looked. Each needs a different response from the user, so the answer says
 * which.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelSearchResponse {

    /** Ties this search to its log lines, stored payloads and AI calls. */
    private String correlationId;

    private List<SearchResultItem> items;

    private int totalResults;

    /**
     * What was actually searched for, and why that was chosen — for example
     * "ASIN B0BDHWDR12" or "title (no usable identifier)".
     */
    private String resolvedQuery;

    /** The identifier type behind {@code resolvedQuery}, or null for a title search. */
    private String resolvedIdentifierType;

    /** Each step the planner ran, in order. */
    private List<SearchStep> steps;

    private Instant startedAt;
    private Instant finishedAt;

    /** A short sentence for the user when the result set is empty or partial. */
    private String note;

    // ---- judging (present only when the request asked for it) ----
    //
    // Judging lives here rather than behind a second endpoint because it is a
    // later step of one search, not a different kind of search. Split apart, a
    // caller had to choose between knowing how the search ran and knowing
    // whether the results are the right product — and the trace is required.

    /**
     * The outcome, once judged:
     * <ul>
     *   <li>{@code FOUND} — at least one listing is this product</li>
     *   <li>{@code NONE_MATCHED} — listings were judged, none matched</li>
     *   <li>{@code NO_LISTINGS} — nothing came back to judge</li>
     *   <li>{@code NOT_JUDGED} — no verdicts: either the caller did not ask for
     *       judging, or it was asked for and the model could not run. The AI
     *       step in {@code steps} distinguishes the two — absent, {@code SKIPPED}
     *       (not configured) or {@code PROVIDER_ERROR}.</li>
     * </ul>
     *
     * <p>{@code matches} and {@code rejected} are null for every value except
     * {@code FOUND} and {@code NONE_MATCHED}. Treat NOT_JUDGED as "these are
     * unverified search results": they may be shown, but never accepted as
     * competitors without a person deciding.</p>
     */
    private String status;

    /** A sentence written to be shown to the user as-is. */
    private String message;

    /** True when trying again could plausibly change the outcome. */
    private boolean retryable;

    /** Listings judged to be this product, best first. Null when not judged. */
    private List<MatchedListing> matches;

    /**
     * Listings judged not to be this product, each with its reason.
     *
     * <p>Returned rather than discarded: "none matched" is far less useful than
     * "none matched, and here is why" — the reasons distinguish a wrong search
     * term from a product that is genuinely not sold here.</p>
     */
    private List<MatchedListing> rejected;

    /** One channel attempt and how it ended. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchStep {

        private String marketplace;

        /** What this step looked for. */
        private String query;

        private String identifierType;

        /**
         * How it ended, as a distinct value rather than prose (FR-SRCH-002):
         * {@code SUCCESS}, {@code NO_RESULT}, {@code UNPRICED},
         * {@code RESTRICTED_ACCESS}, {@code RATE_LIMITED},
         * {@code AUTHENTICATION_ERROR}, {@code PROVIDER_ERROR},
         * {@code CACHED}, {@code SKIPPED}.
         */
        private String outcome;

        private int resultCount;

        /** Whether this step was answered from a previous fetch. */
        private boolean fromCache;

        /** When the underlying data was read from the marketplace. */
        private Instant sourceTimestamp;

        private Long durationMs;

        /** The provider's own message, when there was one. */
        private String detail;
    }
}
