package com.priceintel.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Where a tenant wants its alerts delivered, beyond the in-app bell.
 *
 * <p>An alert that only reaches a bell icon waits for someone to log in, which
 * is the delay alerting exists to remove. Slack and Microsoft Teams are where a
 * pricing team already is.</p>
 *
 * <p>Webhook URLs are stored encrypted and never returned in full: anyone
 * holding one can post into that channel, so they are credentials, not
 * settings.</p>
 */
@Entity
@Table(name = "tenant_notification_channels",
        uniqueConstraints = @UniqueConstraint(name = "uk_notif_channels_tenant",
                columnNames = "tenant_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantNotificationChannels extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Builder.Default
    @Column(name = "slack_enabled", nullable = false)
    private boolean slackEnabled = false;

    @Column(name = "slack_webhook_url", length = 2000)
    private String slackWebhookUrl;

    @Builder.Default
    @Column(name = "teams_enabled", nullable = false)
    private boolean teamsEnabled = false;

    @Column(name = "teams_webhook_url", length = 2000)
    private String teamsWebhookUrl;
}
