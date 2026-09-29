package com.priceintel.backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Update where alerts are delivered.
 *
 * <p>A null webhook URL leaves the stored one untouched, so the UI can save the
 * enabled flags without having to re-send a secret it only ever sees masked.
 * Send an empty string to clear one.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationChannelsRequest {

    private Boolean slackEnabled;
    private String slackWebhookUrl;

    private Boolean teamsEnabled;
    private String teamsWebhookUrl;
}
