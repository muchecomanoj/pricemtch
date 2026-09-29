package com.priceintel.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.priceintel.backend.entity.ActivationToken;

@Repository
public interface ActivationTokenRepository extends JpaRepository<ActivationToken, Long> {

    Optional<ActivationToken> findByTokenHash(String tokenHash);

    /** Unused onboarding checkouts that have a Stripe session — for reconciliation. */
    List<ActivationToken> findByUsedFalseAndStripeSessionIdIsNotNull();

    /** Invalidate any outstanding grants for a user before issuing a new one. */
    @Modifying
    @Query("delete from ActivationToken t where t.userId = :userId")
    void deleteByUserId(Long userId);
}
