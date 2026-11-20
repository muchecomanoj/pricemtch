package com.priceintel.backend.dto.response;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A page of payment history, what it adds up to, and which months have any.
 *
 * <p>The three travel together because the figures above the table have to
 * describe the filter the table is showing. Fetched separately they would be
 * two requests that can disagree, and a total that contradicts the rows beneath
 * it is worse than no total at all.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentHistoryResponse {

    /** The rows themselves, newest first. */
    private PagedResponse<PaymentResponse> payments;

    /** What the filtered set adds up to. */
    private Summary summary;

    /**
     * Every month that holds a payment, newest first, as {@code YYYY-MM}.
     *
     * <p>For the month picker, so it offers the months there is something to
     * see. Not affected by the current filter — a month must not disappear from
     * the list because of a status the user is about to change.</p>
     */
    private List<String> months;

    /**
     * The figures above the table.
     *
     * <p>Counts are always honest. {@code paidAmount} is null when the filtered
     * payments span more than one currency, because there is no single number
     * that would be true; {@code mixedCurrencies} says why, so the screen can
     * drop the total instead of printing a sum of dollars and pounds.</p>
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {

        /** Total actually paid, or null when currencies are mixed. */
        private BigDecimal paidAmount;

        /** The currency of {@code paidAmount}, or null when mixed. */
        private String currency;

        private boolean mixedCurrencies;

        private long paidCount;

        /**
         * Payments awaiting the client.
         *
         * <p>Counted apart from paid because a pending row is a checkout that
         * was started and never completed — an attempt, not money. Shown so a
         * stuck one can still be found.</p>
         */
        private long pendingCount;

        private long failedCount;

        private long cancelledCount;

        /** Every row in the filtered set, whatever its status. */
        private long totalCount;
    }
}
