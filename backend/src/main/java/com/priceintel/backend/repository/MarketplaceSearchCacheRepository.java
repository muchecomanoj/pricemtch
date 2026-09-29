package com.priceintel.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.priceintel.backend.entity.MarketplaceSearchCache;

public interface MarketplaceSearchCacheRepository extends JpaRepository<MarketplaceSearchCache, Long> {

    Optional<MarketplaceSearchCache> findByMarketplaceAndQueryKey(String marketplace, String queryKey);
}
