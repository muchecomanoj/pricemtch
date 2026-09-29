package com.priceintel.backend.dto.request;

import java.math.BigDecimal;
import java.util.List;

import com.priceintel.backend.constants.AlertCondition;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One alert rule applied to many products at once.
 *
 * <p>The same shape as {@link AlertRuleRequest} with {@code productId} replaced
 * by a list. Deliberately not a list of full requests: the screen this serves
 * asks the user for one condition and one threshold, then applies it to a
 * selection. Accepting a heterogeneous batch would invent a capability the
 * product does not have and would make the per-product result harder to read.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkAlertRuleRequest {

    @NotEmpty(message = "Select at least one product")
    private List<Long> productIds;

    @NotNull(message = "condition is required")
    private AlertCondition condition;

    /** "ABSOLUTE" or "PERCENT" (default PERCENT). */
    private String thresholdType;

    private BigDecimal thresholdValue;

    private String note;

    /** Delivery channels: IN_APP, EMAIL, SLACK, TEAMS. Empty = in-app only. */
    private List<String> channels;

    /**
     * Skip products that have no competitor listings yet.
     *
     * <p>Off by default. Such a rule is not wrong — competitors can be added
     * later — but it cannot fire until they are, and a user selecting a whole
     * category rarely means to create rules that sit silent. Either way the
     * result says which products those were.</p>
     */
    private boolean skipProductsWithoutCompetitors;
}
