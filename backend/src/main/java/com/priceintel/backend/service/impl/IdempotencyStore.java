package com.priceintel.backend.service.impl;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceintel.backend.entity.IdempotencyRecord;
import com.priceintel.backend.repository.IdempotencyRecordRepository;
import com.priceintel.backend.security.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The database side of {@link IdempotencyService}.
 *
 * <p>A separate bean because these need {@code REQUIRES_NEW} and are called from
 * that service: Spring's transaction advice lives on the proxy, so a call from
 * one method of a class to another of the same class never reaches it. Kept
 * apart, the claim really does commit independently — which is the whole point,
 * since a claim that rolls back with the caller protects nothing.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyStore {

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * Claims the key, or returns the first call's stored result.
     *
     * <p>The claim is an insert against a unique index: two simultaneous
     * requests race, exactly one wins, and the loser learns it is a duplicate
     * from the constraint rather than from a read that might have run a moment
     * too early.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> Optional<T> claimOrReplay(String scope, String key, Class<T> type) {
        Optional<IdempotencyRecord> existing = repository.findByScopeAndIdemKey(scope, key);
        if (existing.isPresent()) {
            return readStored(existing.get(), type);
        }
        try {
            repository.saveAndFlush(IdempotencyRecord.builder()
                    .scope(scope).idemKey(key)
                    .tenantId(TenantContext.getTenantId())
                    .status("IN_PROGRESS")
                    .build());
            return Optional.empty();
        } catch (DataIntegrityViolationException e) {
            // Lost the race. The winner may not have finished, in which case
            // there is nothing to replay and the caller proceeds — duplicated
            // work is bad, but a request that silently returns nothing is worse.
            return repository.findByScopeAndIdemKey(scope, key)
                    .flatMap(r -> readStored(r, type));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void store(String scope, String key, Object result) {
        try {
            String json = objectMapper.writeValueAsString(result);
            repository.findByScopeAndIdemKey(scope, key).ifPresent(record -> {
                record.setResponseJson(json);
                record.setStatus("COMPLETED");
                repository.save(record);
            });
        } catch (Exception e) {
            // Failing to record the outcome must not fail the request that
            // succeeded; the worst case is that a retry repeats the work.
            log.warn("Could not record idempotent response for {}: {}", scope, e.getMessage());
        }
    }

    /** Frees the key after a failure, so the caller may genuinely retry. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(String scope, String key) {
        repository.findByScopeAndIdemKey(scope, key).ifPresent(repository::delete);
    }

    @Transactional
    public int purgeOlderThan(int days) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
        return repository.countByCreatedAtBefore(cutoff) == 0 ? 0
                : repository.deleteOlderThan(cutoff);
    }

    private <T> Optional<T> readStored(IdempotencyRecord record, Class<T> type) {
        if (!"COMPLETED".equals(record.getStatus()) || record.getResponseJson() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(record.getResponseJson(), type));
        } catch (Exception e) {
            // A stored response we cannot read is worse than none — fall through
            // and do the work rather than failing the request.
            log.warn("Could not replay idempotent response for {}: {}",
                    record.getIdemKey(), e.getMessage());
            return Optional.empty();
        }
    }
}
