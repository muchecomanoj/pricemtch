package com.priceintel.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Completes login: the OTP (or a backup code) plus the challenge token issued
 * by /auth/login when two-factor is required.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TwoFactorVerifyRequest {

    /** 6-digit authenticator code, or one of the user's backup codes. */
    @NotBlank(message = "Code is required")
    private String code;

    @NotBlank(message = "challengeToken is required")
    private String challengeToken;
}
