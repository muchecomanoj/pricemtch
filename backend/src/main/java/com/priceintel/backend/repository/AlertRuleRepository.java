package com.priceintel.backend.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.priceintel.backend.constants.AlertCondition;
import com.priceintel.backend.entity.AlertRule;

@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

    List<AlertRule> findByProductIdOrderByCreatedAtDesc(Long productId);

    List<AlertRule> findByTenantIdOrderByCreatedAtDesc(Long tenantId);

    List<AlertRule> findByActiveTrue();

    /**
     * Existing rules of one condition across many products.
     *
     * <p>One query for the whole batch rather than one per product: a 500-product
     * selection would otherwise spend 500 round trips deciding what to skip.</p>
     */
    List<AlertRule> findByProductIdInAndCondition(
            Collection<Long> productIds, AlertCondition condition);

    /** Rules by id, restricted to one tenant — the guard for every bulk edit. */
    List<AlertRule> findByIdInAndTenantId(Collection<Long> ids, Long tenantId);

    List<AlertRule> findByIdIn(Collection<Long> ids);
}
