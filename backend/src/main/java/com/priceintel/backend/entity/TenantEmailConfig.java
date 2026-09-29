package com.priceintel.backend.entity;

import java.time.Instant;

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
 * A tenant's own SMTP settings (Settings → Email). One row per tenant. When
 * present and enabled, the tenant's emails are sent through this server (from
 * their own address/domain) instead of the platform default. The password is
 * stored AES-encrypted, never in plain text.
 */
@Entity
@Table(name = "tenant_email_configs",
        uniqueConstraints = @UniqueConstraint(name = "uk_email_config_tenant", columnNames = "tenant_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantEmailConfig extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /** When false, the platform default SMTP is used instead. */
    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = false;

    @Column(length = 200)
    private String host;

    @Builder.Default
    @Column(nullable = false)
    private int port = 587;

    @Column(length = 200)
    private String username;

    /** AES-encrypted SMTP password / app-password. */
    @Column(name = "encrypted_password", length = 2000)
    private String encryptedPassword;

    @Column(name = "from_address", length = 200)
    private String fromAddress;

    @Column(name = "from_name", length = 200)
    private String fromName;

    /** STARTTLS (typical for port 587). When false + sslEnabled, uses SSL (465). */
    @Builder.Default
    @Column(name = "start_tls", nullable = false)
    private boolean startTls = true;

    @Builder.Default
    @Column(name = "ssl_enabled", nullable = false)
    private boolean sslEnabled = false;

    /** NOT_CONFIGURED / CONFIGURED / CONNECTED / ERROR — updated by the test action. */
    @Builder.Default
    @Column(length = 20)
    private String status = "NOT_CONFIGURED";

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    @Column(name = "last_message", length = 500)
    private String lastMessage;
}
