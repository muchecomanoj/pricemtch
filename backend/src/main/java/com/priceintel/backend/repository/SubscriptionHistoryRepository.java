package com.priceintel.backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.priceintel.backend.entity.SubscriptionHistory;

@Repository
public interface SubscriptionHistoryRepository extends JpaRepository<SubscriptionHistory, Long> {

    Page<SubscriptionHistory> findByTenantIdOrderByCreatedAtDesc(Long tenantId, Pageable pageable);
}
