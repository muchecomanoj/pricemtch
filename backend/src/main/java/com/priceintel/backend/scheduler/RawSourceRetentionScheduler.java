package com.priceintel.backend.scheduler;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.priceintel.backend.repository.RawSourceRecordRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Drops raw marketplace payloads once they are past their keep-by date.
 *
 * <p>Evidence has a shelf life. Disputes and reprocessing happen within weeks,
 * while the payloads are the largest rows the system writes — kept forever they
 * would become the biggest table in the database and the slowest thing to back
 * up. Ninety days covers the arguments people actually have.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RawSourceRetentionScheduler {

    private final RawSourceRecordRepository repository;
    private final com.priceintel.backend.service.impl.IdempotencyStore idempotencyStore;

    /**
     * How long a replay record is kept.
     *
     * <p>Short: it is only useful while a retry might still arrive, which is
     * minutes, not weeks. Kept at a few days purely so a support question about
     * "did that click go through" can still be answered.</p>
     */
    @Value("${app.idempotency.retention-days:3}")
    private int idempotencyRetentionDays;

    @Value("${app.evidence.retention-days:90}")
    private int retentionDays;

    /** Nightly at 03:20, away from the hourly alert and monitor work. */
    @Scheduled(cron = "0 20 3 * * *")
    @Transactional
    public void purgeExpired() {
        purgeIdempotencyRecords();
        if (retentionDays <= 0) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        long due = repository.countByCreatedAtBefore(cutoff);
        if (due == 0) {
            return;
        }
        int deleted = repository.deleteOlderThan(cutoff);
        log.info("Evidence retention: deleted {} raw source record(s) older than {} days",
                deleted, retentionDays);
    }

    /** Replay records outlive their usefulness within days. */
    private void purgeIdempotencyRecords() {
        if (idempotencyRetentionDays <= 0) {
            return;
        }
        try {
            int deleted = idempotencyStore.purgeOlderThan(idempotencyRetentionDays);
            if (deleted > 0) {
                log.info("Idempotency retention: deleted {} record(s)", deleted);
            }
        } catch (RuntimeException e) {
            // Housekeeping must not take the evidence sweep down with it.
            log.warn("Idempotency purge failed: {}", e.getMessage());
        }
    }
}
