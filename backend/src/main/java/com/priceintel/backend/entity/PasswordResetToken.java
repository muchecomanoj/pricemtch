package com.priceintel.backend.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A password-reset grant: a 6-digit code emailed to the user.
 *
 * <p>The code is stored BCrypt-hashed (never in clear text), attempts are
 * counted to stop brute force, and the grant is single-use.</p>
 */
@Entity
@Table(name = "password_reset_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken extends BaseEntity {

    /** Internal reference (kept unique); the user never sees this. */
    @Column(nullable = false, unique = true, length = 512)
    private String token;

    /** BCrypt hash of the 6-digit verification code. */
    @Column(name = "code_hash", length = 100)
    private String codeHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Instant expiryDate;

    /** Set once the code has been verified; required before the password can be reset. */
    @Builder.Default
    @Column(nullable = false)
    private boolean verified = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean used = false;

    @Builder.Default
    @Column(nullable = false)
    private int attempts = 0;

    public boolean isExpired() {
        return expiryDate.isBefore(Instant.now());
    }
}
