package com.priceintel.backend.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What a bulk monitor create did.
 *
 * <p>Partial success is the normal outcome, not an error: selecting twelve
 * products when three are already monitored should start the other nine and say
 * so, rather than refusing the lot and making the user work out which three.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkMonitorResponse {

    private List<MonitorResponse> created;

    /** Products that were left alone, each with a sentence saying why. */
    private List<Skipped> skipped;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Skipped {

        private Long productId;

        /** Written to be shown as-is, e.g. "Already monitored." */
        private String reason;
    }
}
