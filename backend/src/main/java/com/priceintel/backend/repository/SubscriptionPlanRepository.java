package com.priceintel.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.priceintel.backend.entity.SubscriptionPlan;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {

    Optional<SubscriptionPlan> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<SubscriptionPlan> findByActiveTrueOrderByPriceAsc();
}
