package com.priceintel.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.priceintel.backend.entity.TenantNotificationChannels;

@Repository
public interface TenantNotificationChannelsRepository
        extends JpaRepository<TenantNotificationChannels, Long> {

    Optional<TenantNotificationChannels> findByTenantId(Long tenantId);
}
