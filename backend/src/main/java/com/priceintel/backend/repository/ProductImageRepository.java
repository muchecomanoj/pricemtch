package com.priceintel.backend.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.priceintel.backend.entity.Product;
import com.priceintel.backend.entity.ProductImage;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    /** URL search stage: distinct products whose image URL contains the query. */
    @Query("select distinct pi.product from ProductImage pi "
            + "where lower(pi.url) like lower(concat('%', :url, '%'))")
    List<Product> findProductsByImageUrlContaining(@Param("url") String url, Pageable pageable);

    /** The same, within one company's catalogue. */
    @Query("select distinct pi.product from ProductImage pi "
            + "where pi.product.tenantId = :tenantId "
            + "and lower(pi.url) like lower(concat('%', :url, '%'))")
    List<Product> findProductsByTenantAndImageUrlContaining(@Param("tenantId") Long tenantId,
                                                           @Param("url") String url, Pageable pageable);

    /** Hashed images for image-search, scoped to a tenant (null tenant = all). */
    @Query("select pi from ProductImage pi where pi.hash is not null "
            + "and (:tenantId is null or pi.product.tenantId = :tenantId)")
    List<ProductImage> findHashedForTenant(@Param("tenantId") Long tenantId);

    /** Images not yet hashed (for the one-time backfill). */
    List<ProductImage> findByHashIsNull();
}
