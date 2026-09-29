package com.priceintel.backend.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.priceintel.backend.entity.Tenant;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long>, JpaSpecificationExecutor<Tenant> {

    boolean existsByCompanyCodeIgnoreCase(String companyCode);

    boolean existsByCompanyEmailIgnoreCase(String companyEmail);

    boolean existsByCompanyNameIgnoreCase(String companyName);

    Optional<Tenant> findByCompanyEmailIgnoreCase(String companyEmail);

    /** Tenants with a scheduled downgrade whose effective date has arrived. */
    List<Tenant> findByScheduledPlanCodeIsNotNullAndScheduledPlanEffectiveDateLessThanEqual(LocalDate date);

    /** Candidates for the nightly expiry sweep. */
    List<Tenant> findByStatusAndSubscriptionStatusIn(
            com.priceintel.backend.constants.TenantStatus status,
            java.util.Collection<com.priceintel.backend.constants.SubscriptionStatus> subscriptionStatuses);

    /**
     * Companies with full access today, as ids — for jobs that must skip the
     * rest without loading every tenant. Mirrors SubscriptionAccessService's
     * rule: active, on a trial or paid plan, and paid through {@code cutoff} or
     * later (cutoff = today minus the grace days). No end date means open-ended.
     */
    @org.springframework.data.jpa.repository.Query("SELECT t.id FROM Tenant t "
            + "WHERE t.status = com.priceintel.backend.constants.TenantStatus.ACTIVE "
            + "AND (t.subscriptionStatus IS NULL "
            + "     OR t.subscriptionStatus IN (com.priceintel.backend.constants.SubscriptionStatus.TRIAL, "
            + "                                 com.priceintel.backend.constants.SubscriptionStatus.ACTIVE)) "
            + "AND (COALESCE(t.subscriptionEndDate, t.trialEndDate) IS NULL "
            + "     OR COALESCE(t.subscriptionEndDate, t.trialEndDate) >= :cutoff)")
    List<Long> findFullAccessTenantIds(@org.springframework.data.repository.query.Param("cutoff") LocalDate cutoff);
}
