package com.priceintel.backend.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A one-time client activation grant, emailed as a link + a separate code.
 *
 * <p>Security notes:
 * <ul>
 *   <li>The link token is stored as a SHA-256 hash (deterministic, so we can
 *       look it up) — a database leak cannot reproduce a usable link.</li>
 *   <li>The verification code is stored BCrypt-hashed and compared on submit.</li>
 *   <li>Attempts are counted to stop brute-forcing the 6-digit code.</li>
 * </ul>
 */
@Entity
@Table(name = "activation_tokens", uniqueConstraints = {
        @UniqueConstraint(name = "uk_activation_token_hash", columnNames = "token_hash")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivationToken extends BaseEntity {

    /** SHA-256 of the raw token that appears in the activation URL. */
    @Column(name = "token_hash", nullable = false, length = 100)
    private String tokenHash;

    /** BCrypt hash of the 6-digit verification code. */
    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Builder.Default
    @Column(nullable = false)
    private boolean used = false;

    /** Stripe Checkout Session id for this onboarding payment (for reconciliation). */
    @Column(name = "stripe_session_id", length = 120)
    private String stripeSessionId;

    /** Failed code attempts; the grant locks after too many. */
    @Builder.Default
    @Column(nullable = false)
    private int attempts = 0;

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }
}
