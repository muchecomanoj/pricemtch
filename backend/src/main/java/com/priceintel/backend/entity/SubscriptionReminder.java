package com.priceintel.backend.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One expiry reminder or notice sent to a company — the record that stops a repeat. */
@Entity
@Table(name = "subscription_reminders")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /** The period this was about. Renewal moves it, so reminders restart per period. */
    @Column(name = "paid_through", nullable = false)
    private LocalDate paidThrough;

    /** BEFORE_7, BEFORE_1 (one per configured day), or EXPIRED. */
    @Column(nullable = false, length = 20)
    private String kind;

    @Column(length = 1000)
    private String recipients;

    @Column(name = "sent_at", nullable = false)
    @Builder.Default
    private LocalDateTime sentAt = LocalDateTime.now();
}
