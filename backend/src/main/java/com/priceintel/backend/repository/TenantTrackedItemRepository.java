package com.priceintel.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.priceintel.backend.entity.TenantTrackedItem;

public interface TenantTrackedItemRepository extends JpaRepository<TenantTrackedItem, Long> {

    /**
     * Records that a company saw a listing: inserts the link, or refreshes
     * {@code last_seen_at} if it already exists. One statement, so two searches
     * finishing at the same moment cannot both insert.
     */
    @Modifying
    @Query(value = """
            INSERT INTO tenant_tracked_items (tenant_id, marketplace, storefront, marketplace_item_id)
            VALUES (:tenantId, :marketplace, :storefront, :itemId)
            ON CONFLICT (tenant_id, marketplace, storefront, marketplace_item_id)
            DO UPDATE SET last_seen_at = now()
            """, nativeQuery = true)
    int touch(@Param("tenantId") Long tenantId,
              @Param("marketplace") String marketplace,
              @Param("storefront") String storefront,
              @Param("itemId") String itemId);
}
