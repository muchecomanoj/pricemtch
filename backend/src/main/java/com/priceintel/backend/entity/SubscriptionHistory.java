package com.priceintel.backend.entity;

import java.time.LocalDate;

import com.priceintel.backend.constants.SubscriptionAction;
import com.priceintel.backend.constants.SubscriptionStatus;

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
 * An immutable record of a subscription change for a tenant (assign, upgrade,
 * renew, cancel, etc.) — powers "View Subscription History".
 */
@Entity
@Table(name = "subscription_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionHistory extends BaseEntity {

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionAction action;

    @Column(name = "plan_code", length = 50)
    private String planCode;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SubscriptionStatus status;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(length = 500)
    private String note;
}
