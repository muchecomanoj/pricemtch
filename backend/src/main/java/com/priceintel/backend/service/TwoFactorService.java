package com.priceintel.backend.service;

import com.priceintel.backend.dto.response.AuthResponse;
import com.priceintel.backend.dto.response.TwoFactorEnableResponse;
import com.priceintel.backend.dto.response.TwoFactorSetupResponse;
import com.priceintel.backend.dto.response.TwoFactorStatusResponse;

/**
 * Two-factor authentication (TOTP) use cases.
 */
public interface TwoFactorService {

    /** Is 2FA enabled for the current user? */
    TwoFactorStatusResponse status();

    /** Begin enrollment — returns the secret + otpauth URL for the QR code. */
    TwoFactorSetupResponse setup();

    /** Confirm enrollment with a code from the authenticator; returns backup codes. */
    TwoFactorEnableResponse enable(String code);

    /** Turn 2FA off (requires a valid code). */
    TwoFactorStatusResponse disable(String code);

    /** Complete login: verify the OTP/backup code against the challenge token. */
    AuthResponse verify(String code, String challengeToken);

    /** TOTP codes are generated on the device, so this only re-validates the challenge. */
    void resend(String challengeToken);
}
