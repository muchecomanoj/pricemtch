package com.priceintel.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.priceintel.backend.constants.IdentifierType;
import com.priceintel.backend.entity.ProductIdentifier;

@Repository
public interface ProductIdentifierRepository extends JpaRepository<ProductIdentifier, Long> {

    /** Used by identifier search stages (ASIN/GTIN/EAN/UPC/MPN). */
    List<ProductIdentifier> findByTypeAndNormalizedValue(IdentifierType type, String normalizedValue);

    /** The same, within one company's catalogue. */
    List<ProductIdentifier> findByTenantIdAndTypeAndNormalizedValue(Long tenantId, IdentifierType type,
                                                                   String normalizedValue);

    List<ProductIdentifier> findByProductId(Long productId);

    /**
     * Other products in the same tenant already claiming this identifier.
     *
     * <p>A marketplace identifier addresses exactly one real product, so within
     * one catalogue it must map to one product. Scoped to the tenant on purpose:
     * two clients legitimately stocking the same item both hold its ASIN, and
     * that is what lets them share competitor research.</p>
     *
     * @param excludeProductId the product being saved, so an edit does not
     *                         collide with itself (use a negative id on create)
     */
    @Query("SELECT i FROM ProductIdentifier i "
            + "WHERE i.type = :type AND i.normalizedValue = :value "
            + "AND i.product.tenantId = :tenantId AND i.product.id <> :excludeProductId")
    List<ProductIdentifier> findConflicts(
            @Param("type") IdentifierType type,
            @Param("value") String value,
            @Param("tenantId") Long tenantId,
            @Param("excludeProductId") Long excludeProductId);
}
