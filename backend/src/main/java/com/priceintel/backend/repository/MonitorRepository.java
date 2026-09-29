package com.priceintel.backend.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.priceintel.backend.entity.Monitor;

@Repository
public interface MonitorRepository extends JpaRepository<Monitor, Long> {

    Optional<Monitor> findByProductId(Long productId);

    /**
     * Monitors visible to the caller. {@code tenantId} null = platform owner.
     */
    @Query("SELECT m FROM Monitor m WHERE "
            + "(:tenantId IS NULL OR m.tenantId = :tenantId) AND "
            + "(:productId IS NULL OR m.productId = :productId) AND "
            + "(:enabled IS NULL OR m.enabled = :enabled) "
            + "ORDER BY m.nextRunAt ASC NULLS FIRST")
    List<Monitor> findForList(
            @Param("tenantId") Long tenantId,
            @Param("productId") Long productId,
            @Param("enabled") Boolean enabled);

    /**
     * Monitors that are due, oldest first.
     *
     * <p>Bounded by a page size so one tick can never fan out across an entire
     * catalogue at once — a thousand due monitors would otherwise become a
     * thousand marketplace calls in a single burst.</p>
     */
    @Query("SELECT m FROM Monitor m WHERE m.enabled = true "
            + "AND (m.nextRunAt IS NULL OR m.nextRunAt <= :now) "
            + "ORDER BY m.nextRunAt ASC NULLS FIRST")
    List<Monitor> findDue(@Param("now") Instant now, Pageable pageable);

    /**
     * Due monitors of companies with full access only.
     *
     * <p>The filter has to be in the query, not applied after it. The sweep
     * takes the N most overdue monitors; an expired company's monitors never
     * run, so they would stay the most overdue for ever and fill every batch —
     * and nobody else's monitors would run at all. The tenant condition mirrors
     * TenantRepository.findFullAccessTenantIds.</p>
     */
    @Query("SELECT m FROM Monitor m WHERE m.enabled = true "
            + "AND (m.nextRunAt IS NULL OR m.nextRunAt <= :now) "
            + "AND (m.tenantId IS NULL OR m.tenantId IN ("
            + "    SELECT t.id FROM Tenant t "
            + "    WHERE t.status = com.priceintel.backend.constants.TenantStatus.ACTIVE "
            + "    AND (t.subscriptionStatus IS NULL "
            + "         OR t.subscriptionStatus IN (com.priceintel.backend.constants.SubscriptionStatus.TRIAL, "
            + "                                     com.priceintel.backend.constants.SubscriptionStatus.ACTIVE)) "
            + "    AND (COALESCE(t.subscriptionEndDate, t.trialEndDate) IS NULL "
            + "         OR COALESCE(t.subscriptionEndDate, t.trialEndDate) >= :cutoff))) "
            + "ORDER BY m.nextRunAt ASC NULLS FIRST")
    List<Monitor> findDueForFullAccess(@Param("now") Instant now,
                                       @Param("cutoff") java.time.LocalDate cutoff,
                                       Pageable pageable);

    long countByTenantIdAndEnabledTrue(Long tenantId);
}
