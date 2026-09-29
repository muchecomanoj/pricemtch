package com.priceintel.backend.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Returned once when 2FA is enabled. The backup codes are shown here and never
 * again (only their hashes are stored).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TwoFactorEnableResponse {
    private boolean enabled;
    private List<String> backupCodes;
}
