package com.priceintel.backend.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What a bulk alert-rule request actually did, product by product.
 *
 * <p>A single "success" message is not honest about a batch: creating 12 rules
 * and creating 10 of 12 are different outcomes, and the user has to be able to
 * see which two were left out and why. Every product sent comes back here with
 * a row, including the ones that were skipped.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkAlertRuleResponse {

    private int requested;
    private int created;
    private int skipped;
    private int failed;

    /**
     * How many created rules cannot fire yet because the product has no
     * competitor listings. Counted separately because these were created — they
     * are a warning, not a failure.
     */
    private int createdWithoutCompetitors;

    /** One row per product id sent, in the order they were sent. */
    private List<Row> results;

    /** What happened to one product. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Row {

        private Long productId;

        /** The product's title, so the UI can name it without a second call. */
        private String productTitle;

        /**
         * {@code CREATED}, {@code SKIPPED_DUPLICATE}, {@code SKIPPED_NO_COMPETITORS},
         * {@code NOT_FOUND} or {@code FAILED}.
         */
        private String outcome;

        /** The new rule's id when one was created. */
        private Long ruleId;

        /** The existing rule's id when this was skipped as a duplicate. */
        private Long existingRuleId;

        /**
         * Plain-English explanation, ready to show. Null when the rule was
         * created and there is nothing to explain.
         */
        private String message;

        /**
         * True when the rule was created but the product has no competitor
         * listings, so nothing can trigger it yet.
         */
        private boolean cannotFireYet;
    }
}
