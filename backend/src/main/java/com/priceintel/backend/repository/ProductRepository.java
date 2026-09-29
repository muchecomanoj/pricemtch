package com.priceintel.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.priceintel.backend.entity.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /**
     * Whether this company already uses the SKU. Per company, not platform-wide:
     * another client using the same code is none of this client's business.
     */
    boolean existsByTenantIdAndSkuIgnoreCase(Long tenantId, String sku);

    // ---- search stages ----
    Optional<Product> findBySkuIgnoreCase(String sku);

    List<Product> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    // One company's catalogue only. The unscoped versions above searched every
    // company, so a search in Patuli's account listed Price Matrix's products
    // under "In your catalogue".
    List<Product> findByTenantIdAndSkuIgnoreCase(Long tenantId, String sku);

    List<Product> findByTenantIdAndTitleContainingIgnoreCase(Long tenantId, String title, Pageable pageable);

    /** Every product owned by one tenant. */
    List<Product> findByTenantId(Long tenantId);

    // ---- filter options ----
    //
    // The values actually present in a catalogue, so the Products page dropdowns
    // offer real choices instead of options that match nothing. Tenant-scoped and
    // unscoped variants rather than a nullable parameter, so neither query has to
    // carry a null check.

    @Query("SELECT DISTINCT p.brand FROM Product p "
            + "WHERE p.tenantId = :tenantId AND p.brand IS NOT NULL AND p.brand <> '' "
            + "ORDER BY p.brand")
    List<String> findDistinctBrandsByTenant(@Param("tenantId") Long tenantId);

    @Query("SELECT DISTINCT p.brand FROM Product p "
            + "WHERE p.brand IS NOT NULL AND p.brand <> '' ORDER BY p.brand")
    List<String> findDistinctBrands();

    @Query("SELECT DISTINCT c.name FROM Product p JOIN p.category c "
            + "WHERE p.tenantId = :tenantId AND c.name IS NOT NULL ORDER BY c.name")
    List<String> findDistinctCategoryNamesByTenant(@Param("tenantId") Long tenantId);

    @Query("SELECT DISTINCT c.name FROM Product p JOIN p.category c "
            + "WHERE c.name IS NOT NULL ORDER BY c.name")
    List<String> findDistinctCategoryNames();
}
