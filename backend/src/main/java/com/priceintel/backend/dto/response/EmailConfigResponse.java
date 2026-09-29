package com.priceintel.backend.dto.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A tenant's SMTP settings for display. The password is never returned. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailConfigResponse {
    private Long tenantId;
    private boolean enabled;
    private String host;
    private Integer port;
    private String username;
    /** True if a password is stored (so the UI can show "•••• set" without exposing it). */
    private boolean passwordSet;
    private String fromAddress;
    private String fromName;
    private boolean startTls;
    private boolean sslEnabled;
    private String status;
    private Instant lastCheckedAt;
    private String lastMessage;
    /** True once the tenant has saved a config at least once. */
    private boolean configured;
}
