package com.priceintel.backend.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.priceintel.backend.constants.PaymentStatus;
import com.priceintel.backend.entity.Payment;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Page<Payment> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Payment> findByTenantIdOrderByCreatedAtDesc(Long tenantId, Pageable pageable);

    /** Payments in a status that have a Stripe session — for reconciliation. */
    List<Payment> findByStatusAndStripeSessionIdIsNotNull(PaymentStatus status);
}
