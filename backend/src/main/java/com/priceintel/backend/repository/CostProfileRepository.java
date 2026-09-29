package com.priceintel.backend.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.priceintel.backend.entity.CostProfile;

@Repository
public interface CostProfileRepository extends JpaRepository<CostProfile, Long> {

    /**
     * The profile in force on a date: the newest one that had already taken
     * effect.
     *
     * <p>"At or before" rather than "nearest" — a cost increase entered ahead of
     * the date it applies must not change today's figures.</p>
     */
    @Query("""
            SELECT c FROM CostProfile c
            WHERE c.productId = :productId AND c.validFrom <= :on
            ORDER BY c.validFrom DESC
            """)
    List<CostProfile> findEffectiveOn(@Param("productId") Long productId,
                                      @Param("on") LocalDate on,
                                      Pageable pageable);

    /** Every version for a product, newest effective date first. */
    List<CostProfile> findByProductIdOrderByValidFromDesc(Long productId);

    Optional<CostProfile> findByProductIdAndValidFrom(Long productId, LocalDate validFrom);
}
