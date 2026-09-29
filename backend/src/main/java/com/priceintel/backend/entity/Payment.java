package com.priceintel.backend.entity;

import java.math.BigDecimal;
import java.time.Instant;

import com.priceintel.backend.constants.BillingCycle;
import com.priceintel.backend.constants.PaymentStatus;
import com.priceintel.backend.constants.PaymentType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A payment (or intended payment) for a tenant's subscription — powers the
 * super admin's payment history. One row per checkout: created PENDING when a
 * pay link is issued, flipped to PAID by the Stripe webhook.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /** The plan this payment is for. */
    @Column(name = "plan_code", length = 50)
    private String planCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", length = 10)
    private BillingCycle billingCycle;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 10)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentType type;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    /** e.g. "card", "mock", "manual". */
    @Column(length = 30)
    private String method;

    /** Stripe Checkout Session id, when applicable. */
    @Column(name = "stripe_session_id", length = 120)
    private String stripeSessionId;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(length = 300)
    private String note;
}
