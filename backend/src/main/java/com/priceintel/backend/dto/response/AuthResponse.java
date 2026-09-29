package com.priceintel.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Returned by login / register / refresh. Contains the tokens the client needs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    /**
     * True when the password was correct but two-factor verification is still
     * required. In that case no tokens are issued — use {@link #challengeToken}
     * with POST /auth/2fa/verify to complete login.
     */
    private boolean twoFactorRequired;

    /** Short-lived token that holds the session until the OTP is verified. */
    private String challengeToken;

    private String accessToken;
    private String refreshToken;

    @Builder.Default
    private String tokenType = "Bearer";

    /** Access token lifetime in seconds. */
    private long expiresIn;

    private UserResponse user;
}
