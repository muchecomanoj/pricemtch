package com.priceintel.backend.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM encryption for secrets stored at rest (e.g. marketplace API
 * credentials). The key is derived from {@code app.crypto.secret}; set a strong
 * value via the CRYPTO_SECRET env var in any real environment.
 */
@Component
public class EncryptionService {

    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12;      // 96-bit nonce for GCM
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public EncryptionService(@Value("${app.crypto.secret:change-me-dev-secret}") String secret) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(hash, "AES"); // 32 bytes -> AES-256
        } catch (Exception e) {
            throw new IllegalStateException("Could not initialise encryption key", e);
        }
    }

    /** Returns Base64(iv || ciphertext+tag), or null for null input. */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    /** Reverses {@link #encrypt}; returns null for null input. */
    public String decrypt(String encoded) {
        if (encoded == null) {
            return null;
        }
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            byte[] iv = new byte[IV_LEN];
            System.arraycopy(all, 0, iv, 0, IV_LEN);
            byte[] ct = new byte[all.length - IV_LEN];
            System.arraycopy(all, IV_LEN, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }
}
