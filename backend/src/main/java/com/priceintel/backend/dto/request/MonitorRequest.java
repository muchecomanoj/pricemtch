package com.priceintel.backend.dto.request;

import java.util.List;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Create or update a scheduled competitor check for a product. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitorRequest {

    /** Required on create; ignored on update (a monitor cannot change product). */
    private Long productId;

    /** AMAZON, EBAY. Empty means every enabled marketplace. */
    private List<String> markets;

    @Positive(message = "maxResults must be positive")
    private Integer maxResults;

    /**
     * Minutes between checks. Rejected if below the tenant plan's floor —
     * frequency is a plan entitlement, not a free choice, because each run
     * spends shared marketplace quota.
     */
    @Positive(message = "intervalMinutes must be positive")
    private Integer intervalMinutes;

    /** Omit on create to default to enabled. */
    private Boolean enabled;
}
