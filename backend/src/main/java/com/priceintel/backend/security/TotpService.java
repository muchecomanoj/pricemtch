package com.priceintel.backend.security;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base32;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * TOTP (RFC 6238) — the algorithm behind Google Authenticator / Authy.
 * Codes are generated on the user's device from a shared secret; nothing is
 * transmitted, so there is no email/SMS dependency.
 */
@Slf4j
@Service
public class TotpService {

    private static final int SECRET_BYTES = 20;      // 160-bit secret
    private static final int DIGITS = 6;
    private static final int PERIOD_SECONDS = 30;
    /** Accept the previous/next step to tolerate clock drift. */
    private static final int DRIFT_STEPS = 1;
    private static final String ISSUER = "PriceIntel";

    /** Generates a new Base32 shared secret. */
    public String generateSecret() {
        byte[] buffer = new byte[SECRET_BYTES];
        new SecureRandom().nextBytes(buffer);
        return new Base32().encodeToString(buffer).replace("=", "");
    }

    /**
     * Builds the otpauth:// URL the frontend renders as a QR code, e.g.
     * {@code otpauth://totp/PriceIntel:user@x.com?secret=...&issuer=PriceIntel}
     */
    public String buildOtpAuthUrl(String email, String secret) {
        String label = encode(ISSUER + ":" + email);
        return "otpauth://totp/" + label
                + "?secret=" + secret
                + "&issuer=" + encode(ISSUER)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + PERIOD_SECONDS;
    }

    /** True if the code is valid for the secret now (allowing ±1 step drift). */
    public boolean verifyCode(String secret, String code) {
        if (secret == null || code == null || !code.matches("\\d{" + DIGITS + "}")) {
            return false;
        }
        long currentStep = Instant.now().getEpochSecond() / PERIOD_SECONDS;
        for (int i = -DRIFT_STEPS; i <= DRIFT_STEPS; i++) {
            if (generateCode(secret, currentStep + i).equals(code)) {
                return true;
            }
        }
        return false;
    }

    /** Generates the code for a given time step (exposed for testing/support). */
    public String generateCode(String secret, long timeStep) {
        try {
            byte[] key = new Base32().decode(secret);
            byte[] data = ByteBuffer.allocate(8).putLong(timeStep).array();

            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);

            // Dynamic truncation (RFC 4226 §5.4)
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % (int) Math.pow(10, DIGITS);
            return String.format("%0" + DIGITS + "d", otp);
        } catch (Exception e) {
            log.error("Failed to generate TOTP code", e);
            throw new IllegalStateException("Could not generate TOTP code", e);
        }
    }

    /** Current code for a secret — used by tests/support tooling. */
    public String currentCode(String secret) {
        return generateCode(secret, Instant.now().getEpochSecond() / PERIOD_SECONDS);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
