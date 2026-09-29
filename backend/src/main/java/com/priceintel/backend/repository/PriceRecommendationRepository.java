package com.priceintel.backend.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.priceintel.backend.constants.RecommendationStatus;
import com.priceintel.backend.entity.PriceRecommendation;

@Repository
public interface PriceRecommendationRepository extends JpaRepository<PriceRecommendation, Long> {

    List<PriceRecommendation> findByProductIdOrderByCreatedAtDesc(Long productId);

    /**
     * Tenant-wide list for the Recommendations page; optional status filter.
     *
     * <p>Joins products to scope by owner rather than denormalising a tenant
     * column: this list is read a page at a time by a human, so the join costs
     * nothing worth optimising away. {@code tenantId} null = platform owner.</p>
     */
    @Query("SELECT r FROM PriceRecommendation r, Product p "
            + "WHERE p.id = r.productId "
            + "AND (:status IS NULL OR r.status = :status) "
            + "AND (:tenantId IS NULL OR p.tenantId = :tenantId)")
    Page<PriceRecommendation> findForList(
            @Param("status") RecommendationStatus status,
            @Param("tenantId") Long tenantId,
            Pageable pageable);

    /** Whether a product already has a recommendation in the given status. */
    boolean existsByProductIdAndStatus(Long productId, RecommendationStatus status);

    /** The pending draft for a product, so regenerating replaces it rather than stacking. */
    java.util.Optional<PriceRecommendation> findFirstByProductIdAndStatusOrderByIdDesc(
            Long productId, RecommendationStatus status);
}
