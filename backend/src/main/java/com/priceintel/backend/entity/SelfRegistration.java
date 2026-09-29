package com.priceintel.backend.entity;

import java.time.Instant;

import com.priceintel.backend.constants.BillingCycle;
import com.priceintel.backend.constants.SelfRegistrationStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A landing-page self-signup held in-progress. Carries everything the client
 * enters; a real {@code Tenant} + {@code User} are materialized from it ONLY
 * after payment succeeds. Secrets are hashed (token SHA-256, code + password
 * BCrypt).
 */
@Entity
@Table(name = "self_registrations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_self_reg_token", columnNames = "token_hash")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SelfRegistration extends BaseEntity {

    @Column(name = "token_hash", nullable = false, length = 100)
    private String tokenHash;

    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    /** Set once the client chooses a password (BCrypt). */
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(nullable = false, length = 150)
    private String email;

    // ---- company profile ----
    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;
    @Column(name = "company_phone", length = 30)
    private String companyPhone;
    @Column(name = "company_address", length = 500)
    private String companyAddress;
    @Column(length = 100)
    private String city;
    @Column(length = 100)
    private String state;
    @Column(length = 100)
    private String country;
    @Column(name = "postal_code", length = 20)
    private String postalCode;
    @Column(length = 60)
    private String timezone;
    @Column(length = 10)
    private String currency;
    @Column(length = 200)
    private String website;
    @Column(name = "contact_person", length = 150)
    private String contactPerson;
    @Column(length = 100)
    private String industry;
    @Column(length = 40)
    private String gstin;
    @Column(name = "tax_id", length = 40)
    private String taxId;

    // ---- chosen plan ----
    @Column(name = "plan_code", nullable = false, length = 50)
    private String planCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false, length = 10)
    private BillingCycle billingCycle;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SelfRegistrationStatus status = SelfRegistrationStatus.PENDING_VERIFICATION;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Builder.Default
    @Column(nullable = false)
    private int attempts = 0;

    /** The tenant created on completion (for idempotency). */
    @Column(name = "created_tenant_id")
    private Long createdTenantId;

    /** Stripe Checkout Session id for this signup payment (for reconciliation). */
    @Column(name = "stripe_session_id", length = 120)
    private String stripeSessionId;

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }
}
