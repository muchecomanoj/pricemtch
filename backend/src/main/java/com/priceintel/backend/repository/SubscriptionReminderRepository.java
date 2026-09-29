package com.priceintel.backend.repository;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;

import com.priceintel.backend.entity.SubscriptionReminder;

public interface SubscriptionReminderRepository extends JpaRepository<SubscriptionReminder, Long> {

    boolean existsByTenantIdAndPaidThroughAndKind(Long tenantId, LocalDate paidThrough, String kind);
}
