package com.priceintel.backend.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.priceintel.backend.constants.SelfRegistrationStatus;
import com.priceintel.backend.entity.SelfRegistration;

@Repository
public interface SelfRegistrationRepository extends JpaRepository<SelfRegistration, Long> {

    Optional<SelfRegistration> findByTokenHash(String tokenHash);

    /** Signups in the given status that have a Stripe session — for reconciliation. */
    List<SelfRegistration> findByStatusAndStripeSessionIdIsNotNull(SelfRegistrationStatus status);

    /** Clear abandoned/incomplete signups for an email before starting a new one. */
    @Modifying
    @Query("delete from SelfRegistration r where lower(r.email) = lower(:email) and r.status <> "
            + "com.priceintel.backend.constants.SelfRegistrationStatus.COMPLETED")
    void deleteIncompleteByEmail(String email);
}
