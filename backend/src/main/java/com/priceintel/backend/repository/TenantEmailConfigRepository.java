package com.priceintel.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.priceintel.backend.entity.TenantEmailConfig;

@Repository
public interface TenantEmailConfigRepository extends JpaRepository<TenantEmailConfig, Long> {

    Optional<TenantEmailConfig> findByTenantId(Long tenantId);
}
