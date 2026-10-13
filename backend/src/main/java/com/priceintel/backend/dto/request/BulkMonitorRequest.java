package com.priceintel.backend.dto.request;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Start monitoring several products on the same settings.
 *
 * <p>One set of settings for every product, because the screen that uses this
 * is "monitor these ten products the same way". A product needing different
 * settings is a separate monitor, created on its own.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkMonitorRequest {

    /**
     * The products to monitor.
     *
     * <p>Capped because every monitor created here becomes recurring
     * marketplace and AI usage, and a runaway selection would be felt as a
     * bill rather than as an error.</p>
     */
    @NotEmpty(message = "Select at least one product to monitor")
    @Size(max = 200, message = "Monitor at most 200 products at a time")
    private List<Long> productIds;

    /** AMAZON, EBAY. Empty means every enabled marketplace. */
    private List<String> markets;

    @Positive(message = "maxResults must be positive")
    @Min(value = 1, message = "maxResults must be at least 1")
    @Max(value = 50, message = "maxResults cannot be more than 50")
    private Integer maxResults;

    /** Minutes between checks. Rejected if below the plan's floor. */
    @Positive(message = "intervalMinutes must be positive")
    private Integer intervalMinutes;

    /** Omit to create them enabled. */
    private Boolean enabled;
}
