package com.priceintel.backend.service.impl;

import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.priceintel.backend.entity.BulkSearchJob;
import com.priceintel.backend.repository.BulkSearchJobRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Picks up bulk searches left unfinished by a restart.
 *
 * <p>A background job survives nothing on its own: a restart mid-run leaves one
 * PROCESSING forever, and one queued moments before shutdown stays PENDING.
 * Both look identical to a user watching a progress bar that never moves. Rows
 * already searched are skipped, so resuming costs only the work actually lost.</p>
 *
 * <p><b>A separate bean on purpose.</b> Calling the {@code @Async} method from
 * inside its own class bypasses the proxy that makes it asynchronous — it then
 * runs inline on the startup thread, which both blocks the application from
 * accepting requests and searches marketplaces before the connectors are ready.
 * That happened: three rows "completed" in zero seconds having found nothing.
 * A call between two beans goes through the proxy and lands on the worker.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BulkSearchResumer {

    private final BulkSearchJobRepository jobRepo;
    private final AsyncBulkSearchProcessor processor;

    @EventListener(ApplicationReadyEvent.class)
    public void resumeUnfinished() {
        List<BulkSearchJob> stuck = jobRepo.findAll().stream()
                .filter(j -> j.getStatus() == BulkSearchJob.Status.PENDING
                        || j.getStatus() == BulkSearchJob.Status.PROCESSING)
                .toList();
        if (stuck.isEmpty()) {
            return;
        }
        log.info("Resuming {} unfinished bulk search job(s): {}", stuck.size(),
                stuck.stream().map(BulkSearchJob::getId).toList());
        stuck.forEach(j -> processor.run(j.getId()));
    }
}
