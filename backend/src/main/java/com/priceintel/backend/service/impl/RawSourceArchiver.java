package com.priceintel.backend.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.priceintel.backend.entity.RawSourceRecord;
import com.priceintel.backend.repository.RawSourceRecordRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Keeps the payload a marketplace actually returned, so a stored price can be
 * proved rather than merely asserted (FR-REPORT-001).
 *
 * <p>Without this, "your price was wrong" has no answer beyond repeating the
 * number we derived. It also means a change to parsing or match scoring can only
 * be applied to new data — old rows would need re-fetching, spending quota to
 * recover something we already had.</p>
 *
 * <p>Not everything is worth keeping. Catalog payloads are the largest and the
 * least disputed; prices and fees are small and are what the money arguments are
 * about, so those are retained and the rest is dropped.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RawSourceArchiver {

    /**
     * Operations whose payloads are kept. Catalog responses are deliberately
     * absent: they are an order of magnitude larger, change rarely, and nobody
     * disputes a product title the way they dispute a price.
     */
    private static final Set<String> RETAINED = Set.of("PRICING", "OFFERS", "FEES");

    /** External ids are stored in a 200-char column. */
    private static final int MAX_EXTERNAL_ID = 200;

    private final RawSourceRecordRepository repository;

    @Value("${app.evidence.enabled:true}")
    private boolean enabled;

    /**
     * Stores one payload, if this operation is retained and the payload differs
     * from the last one held for the same key.
     *
     * <p>Runs in its own transaction so the evidence survives a caller that later
     * rolls back — a failed search is exactly when the payload is most worth
     * having. Never throws: losing the archive must not lose the price.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void archive(String source, String operation, String externalId, String payload) {
        if (!enabled || payload == null || payload.isBlank()
                || externalId == null || externalId.isBlank()
                || !RETAINED.contains(operation)) {
            return;
        }
        try {
            String id = truncate(externalId);
            String checksum = sha256(payload);

            // An unchanged price re-fetched every six hours would otherwise
            // store the same bytes forever. The observation times live in
            // listing_price_snapshots; this table answers "what did they say",
            // so one copy per distinct answer is the whole of it.
            if (repository.existsBySourceAndOperationAndExternalIdAndChecksum(
                    source, operation, id, checksum)) {
                return;
            }
            repository.save(RawSourceRecord.builder()
                    .source(source)
                    .operation(operation)
                    .externalId(id)
                    .checksum(checksum)
                    .payload(payload)
                    .build());
        } catch (Exception e) {
            log.warn("Archiving {} {} for {} failed: {}", source, operation, externalId, e.getMessage());
        }
    }

    private String truncate(String s) {
        String trimmed = s.trim();
        return trimmed.length() > MAX_EXTERNAL_ID ? trimmed.substring(0, MAX_EXTERNAL_ID) : trimmed;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            // Hashing cannot realistically fail; a null checksum simply means
            // this payload is stored without deduplication.
            return null;
        }
    }
}
