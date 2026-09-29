package com.priceintel.backend.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What happened when a searched row was saved to the catalogue.
 *
 * <p>"That product already exists" is an <em>answer</em>, not an error. Returned
 * as a normal result with the existing product and a field-by-field comparison,
 * so the screen can ask "update it?" instead of showing a red banner the user
 * can only dismiss.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveRowResponse {

    /**
     * <ul>
     *   <li>{@code CREATED} — a new product was added</li>
     *   <li>{@code EXISTS} — nothing was written; the product is already in the
     *       catalogue and {@link #differences} says how it differs. Call save
     *       again with {@code update=true} to apply them.</li>
     *   <li>{@code UPDATED} — the existing product was updated</li>
     *   <li>{@code UNCHANGED} — it already exists and nothing differs</li>
     * </ul>
     */
    private String status;

    private Long productId;
    private String productSku;
    private String productTitle;

    /** How the existing product was recognised, e.g. "ASIN B00FLYWNYQ" or "SKU PAT-2001". */
    private String matchedOn;

    /** A sentence written to be shown to the user as-is. */
    private String message;

    /**
     * The fields that would change, empty when nothing would.
     *
     * <p>Present so the confirmation dialog can show exactly what a "yes" does.
     * A prompt that says only "update it?" asks the user to approve something
     * they cannot see.</p>
     */
    private List<FieldDifference> differences;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldDifference {

        /** {@code title}, {@code brand}, {@code ourPrice}, or {@code identifier.UPC}. */
        private String field;

        private String current;

        private String incoming;

        /**
         * True when the incoming value is a marketplace price rather than a
         * fact about the product.
         *
         * <p>The price found on Amazon is what a seller is charging there, which
         * is not necessarily what this catalogue should sell at. Flagged so the
         * dialog can mark it for a second look rather than presenting it beside
         * a title correction as though the two were equally safe.</p>
         */
        private boolean advisory;
    }
}
