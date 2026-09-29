package com.priceintel.backend.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.priceintel.backend.entity.IdempotencyRecord;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    Optional<IdempotencyRecord> findByScopeAndIdemKey(String scope, String idemKey);

    /** Retention sweep — a replay window measured in days is generous enough. */
    @Modifying
    @Query("DELETE FROM IdempotencyRecord r WHERE r.createdAt < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);

    long countByCreatedAtBefore(LocalDateTime before);
}
