package com.priceintel.backend.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A marketplace's current status for the Settings → Marketplaces screen.
 * Secret credential values are never returned — {@code credentials} shows only
 * non-secret values and a masked hint (e.g. "••••1234") for secrets that are set.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketplaceStatusResponse {
    private String code;                 // provider enum name, e.g. "AMAZON"
    private String name;                 // display name
    private String description;
    private boolean enabled;
    private boolean configured;          // has credentials stored
    private String status;               // NOT_CONFIGURED | CONNECTED | ERROR
    private Instant lastChecked;
    private String lastMessage;
    private List<String> capabilities;
    /** Non-secret values shown as-is; secret values masked (or absent if unset). */
    private Map<String, String> credentials;
}
