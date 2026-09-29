package com.priceintel.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.priceintel.backend.entity.TenantCostModel;

@Repository
public interface TenantCostModelRepository extends JpaRepository<TenantCostModel, Long> {

    Optional<TenantCostModel> findByTenantId(Long tenantId);
}
