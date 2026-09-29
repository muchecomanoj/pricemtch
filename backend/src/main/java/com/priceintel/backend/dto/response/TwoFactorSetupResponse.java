package com.priceintel.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Enrollment payload: the manual-entry secret plus the otpauth URL to render
 * as a QR code.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TwoFactorSetupResponse {
    private String secret;
    private String otpauthUrl;
}
