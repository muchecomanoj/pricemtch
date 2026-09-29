package com.priceintel.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.priceintel.backend.entity.PricingPolicy;

public interface PricingPolicyRepository extends JpaRepository<PricingPolicy, Long> {

    List<PricingPolicy> findByTenantIdAndActiveTrue(Long tenantId);

    List<PricingPolicy> findByTenantId(Long tenantId);

    Optional<PricingPolicy> findByTenantIdAndProductIdAndMarketplace(
            Long tenantId, Long productId, String marketplace);
}
