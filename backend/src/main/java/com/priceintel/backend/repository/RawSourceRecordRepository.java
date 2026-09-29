package com.priceintel.backend.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.priceintel.backend.entity.RawSourceRecord;

@Repository
public interface RawSourceRecordRepository extends JpaRepository<RawSourceRecord, Long> {

    List<RawSourceRecord> findBySourceAndExternalIdOrderByCreatedAtDesc(String source, String externalId);

    /**
     * Records for an id, including the batch calls it was part of.
     *
     * <p>Competitive pricing is fetched up to 20 ASINs at a time and stored under
     * the whole list, so an exact match would hide the very payload that carries
     * the price. The {@code LIKE} finds those; the equality keeps single-id
     * lookups exact.</p>
     */
    @Query("SELECT r FROM RawSourceRecord r WHERE r.source = :source "
            + "AND (r.externalId = :externalId OR r.externalId LIKE CONCAT('%', :externalId, '%')) "
            + "ORDER BY r.createdAt DESC")
    List<RawSourceRecord> findForExternalId(@Param("source") String source,
            @Param("externalId") String externalId);

    /** True when this exact payload is already held for this key. */
    boolean existsBySourceAndOperationAndExternalIdAndChecksum(
            String source, String operation, String externalId, String checksum);

    /** Retention sweep: drops payloads past their keep-by date. */
    @Modifying
    @Query("DELETE FROM RawSourceRecord r WHERE r.createdAt < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);

    long countByCreatedAtBefore(LocalDateTime before);
}
