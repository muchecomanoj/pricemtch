package com.priceintel.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.priceintel.backend.constants.MarketplaceProvider;
import com.priceintel.backend.entity.MarketplaceConfig;

@Repository
public interface MarketplaceConfigRepository extends JpaRepository<MarketplaceConfig, Long> {

    Optional<MarketplaceConfig> findByProvider(MarketplaceProvider provider);
}
