package com.priceintel.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Where alerts are delivered. Webhook URLs come back masked — enough to
 * recognise which one is configured, never enough to post with.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationChannelsResponse {

    private Channel slack;
    private Channel teams;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Channel {
        private boolean enabled;
        /** True when a URL is stored, so the UI can show "configured". */
        private boolean configured;
        /** Masked form, e.g. "https://hooks.slack.com/…/aB3xY". Never the full URL. */
        private String webhookUrlMasked;
    }
}
