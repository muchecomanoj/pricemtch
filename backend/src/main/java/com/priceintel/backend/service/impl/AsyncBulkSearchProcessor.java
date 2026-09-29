package com.priceintel.backend.service.impl;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceintel.backend.dto.request.ChannelSearchRequest;
import com.priceintel.backend.dto.request.Destination;
import com.priceintel.backend.dto.response.ChannelSearchResponse;
import com.priceintel.backend.entity.BulkSearchJob;
import com.priceintel.backend.entity.BulkSearchRow;
import com.priceintel.backend.repository.BulkSearchJobRepository;
import com.priceintel.backend.repository.BulkSearchRowRepository;
import com.priceintel.backend.security.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs a bulk marketplace search in the background, one row at a time.
 *
 * <h2>Why it is slow, and why that is correct</h2>
 * <p>Rows run sequentially rather than in parallel. Both providers meter by the
 * minute — Amazon returns 429s under burst, and judging costs ~2,400 tokens
 * against an 8,000/minute budget, so roughly three rows a minute. Firing rows
 * concurrently would not finish sooner; it would spend the same quota faster and
 * then fail, converting a slow success into a fast partial failure.</p>
 *
 * <p>The existing rate limiters inside the connectors and the AI gateway already
 * pace each call, so this loop simply lets them do it.</p>
 *
 * <h2>Isolation</h2>
 * <p>A row that fails is recorded and the run continues. A search that finds
 * nothing is a <em>result</em>, not an error, and is counted separately —
 * "we looked and there is nothing" and "we could not look" lead to different
 * actions and must not share a number.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncBulkSearchProcessor {

    private final BulkSearchJobRepository jobRepo;
    private final BulkSearchRowRepository rowRepo;
    private final ChannelSearchPlanner planner;
    private final ObjectMapper objectMapper;

    /** Persist progress this often, so the screen moves without a write per row. */
    private static final int PROGRESS_FLUSH_EVERY = 5;

    @Async("importTaskExecutor")
    public void run(Long jobId) {
        BulkSearchJob job = jobRepo.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("Bulk search job {} vanished before it started", jobId);
            return;
        }
        // The tenant lives in a ThreadLocal, which does not follow work across
        // the async boundary. Without this every search runs unscoped.
        try {
            if (job.getTenantId() != null) {
                TenantContext.set(job.getTenantId(), false);
            }
            process(job);
        } finally {
            TenantContext.clear();
        }
    }

    private void process(BulkSearchJob job) {
        job.setStatus(BulkSearchJob.Status.PROCESSING);
        job.setStartedAt(LocalDateTime.now());
        jobRepo.save(job);

        List<BulkSearchRow> rows = rowRepo.findByJobIdOrderByRowNumberAsc(job.getId());
        Destination destination = destinationOf(job);
        long started = System.currentTimeMillis();

        try {
            for (int i = 0; i < rows.size(); i++) {
                BulkSearchRow row = rows.get(i);
                // Skip anything already searched, so a job resumed after a
                // restart continues rather than paying for every row twice.
                if (row.getStatus() != BulkSearchRow.Status.PENDING) {
                    continue;
                }
                searchOne(job, row, destination);

                job.setProcessedRows(i + 1);
                if ((i + 1) % PROGRESS_FLUSH_EVERY == 0 || i == rows.size() - 1) {
                    recount(job);
                    jobRepo.save(job);
                }
            }
            recount(job);
            job.setStatus(job.getErrorRows() > 0
                    ? BulkSearchJob.Status.COMPLETED_WITH_ERRORS : BulkSearchJob.Status.COMPLETED);
            job.setMessage(String.format(
                    "Searched %d row(s): %d found a match, %d found nothing, %d failed",
                    job.getTotalRows(), job.getFoundRows(), job.getEmptyRows(), job.getErrorRows()));
        } catch (Exception e) {
            // Only reached if something outside a single row's handling breaks.
            // Rows already searched keep their results — the job is marked
            // COMPLETED_WITH_ERRORS rather than FAILED so that work is not
            // hidden behind a status nobody would think to open.
            log.error("Bulk search job {} stopped early", job.getId(), e);
            job.setStatus(job.getProcessedRows() > 0
                    ? BulkSearchJob.Status.COMPLETED_WITH_ERRORS : BulkSearchJob.Status.FAILED);
            job.setMessage("Stopped after " + job.getProcessedRows() + " of "
                    + job.getTotalRows() + " row(s): " + e.getMessage());
        } finally {
            job.setFinishedAt(LocalDateTime.now());
            jobRepo.save(job);
            log.info("Bulk search job {} finished in {}s: {}", job.getId(),
                    (System.currentTimeMillis() - started) / 1000, job.getStatus());
        }
    }

    /**
     * Recomputes the counters from the rows themselves.
     *
     * <p>Derived rather than incremented so a resumed job reports the truth: an
     * incrementing counter would start again from whatever was last flushed and
     * count the rows searched before the restart a second time, or not at all.</p>
     */
    private void recount(BulkSearchJob job) {
        int found = 0;
        int empty = 0;
        int errors = 0;
        int processed = 0;
        for (BulkSearchRow r : rowRepo.findByJobIdOrderByRowNumberAsc(job.getId())) {
            if (r.getStatus() == BulkSearchRow.Status.PENDING) {
                continue;
            }
            processed++;
            switch (r.getStatus()) {
                case FOUND -> found++;
                case ERROR -> errors++;
                default -> empty++;
            }
        }
        job.setProcessedRows(processed);
        job.setFoundRows(found);
        job.setEmptyRows(empty);
        job.setErrorRows(errors);
    }

    private void searchOne(BulkSearchJob job, BulkSearchRow row, Destination destination) {
        try {
            ChannelSearchRequest request = ChannelSearchRequest.builder()
                    .identifiers(identifiersOf(row))
                    .title(row.getInputTitle())
                    .brand(row.getInputBrand())
                    .judge(job.isJudge())
                    .destination(destination)
                    .maxResults(12)
                    .build();

            ChannelSearchResponse result = planner.search(request);

            row.setCorrelationId(result.getCorrelationId());
            row.setResolvedQuery(result.getResolvedQuery());
            row.setResolvedIdentifierType(result.getResolvedIdentifierType());
            row.setMessage(truncate(result.getMessage() != null
                    ? result.getMessage() : result.getNote()));
            row.setMatchCount(result.getMatches() == null ? 0 : result.getMatches().size());
            row.setRejectedCount(result.getRejected() == null ? 0 : result.getRejected().size());
            row.setMatchesJson(toJson(result.getMatches()));
            row.setRejectedJson(toJson(result.getRejected()));
            // "Searched and matched nothing" is a result, not a failure, and is
            // counted apart from "could not search" by recount() above.
            row.setStatus(statusOf(result));
            row.setSearchedAt(Instant.now());
        } catch (RuntimeException e) {
            log.warn("Bulk search row {} of job {} failed: {}",
                    row.getRowNumber(), job.getId(), e.getMessage());
            row.setStatus(BulkSearchRow.Status.ERROR);
            row.setMessage(truncate(e.getMessage()));
            row.setSearchedAt(Instant.now());
        }
        rowRepo.save(row);
    }

    private BulkSearchRow.Status statusOf(ChannelSearchResponse r) {
        String s = r.getStatus();
        if (s == null) {
            return BulkSearchRow.Status.NO_LISTINGS;
        }
        return switch (s) {
            case "FOUND" -> BulkSearchRow.Status.FOUND;
            case "NONE_MATCHED" -> BulkSearchRow.Status.NONE_MATCHED;
            case "NOT_JUDGED" -> BulkSearchRow.Status.NOT_JUDGED;
            default -> BulkSearchRow.Status.NO_LISTINGS;
        };
    }

    /**
     * The identifiers this row supplied.
     *
     * <p>All of them are passed: the planner tries them in its own priority
     * order and moves on when one resolves nothing, so a row carrying both a UPC
     * and an ASIN gets two chances rather than one.</p>
     */
    private Map<String, String> identifiersOf(BulkSearchRow row) {
        Map<String, String> ids = new LinkedHashMap<>();
        put(ids, "ASIN", row.getInputAsin());
        put(ids, "UPC", row.getInputUpc());
        put(ids, "EAN", row.getInputEan());
        put(ids, "GTIN", row.getInputGtin());
        put(ids, "MPN", row.getInputMpn());
        return ids.isEmpty() ? null : ids;
    }

    private void put(Map<String, String> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value.trim());
        }
    }

    private Destination destinationOf(BulkSearchJob job) {
        if (job.getDestinationCountry() == null && job.getDestinationPostalCode() == null) {
            return null;
        }
        return Destination.builder()
                .country(job.getDestinationCountry())
                .postalCode(job.getDestinationPostalCode())
                .build();
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.debug("Could not serialise bulk search results: {}", e.getMessage());
            return null;
        }
    }

    private String truncate(String s) {
        return s == null || s.length() <= 1000 ? s : s.substring(0, 1000);
    }
}
