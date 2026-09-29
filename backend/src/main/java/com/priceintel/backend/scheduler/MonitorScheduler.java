package com.priceintel.backend.scheduler;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.priceintel.backend.entity.Monitor;
import com.priceintel.backend.security.TenantContext;
import com.priceintel.backend.service.impl.MonitorHealthNotifier;
import com.priceintel.backend.service.impl.MonitorService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs competitor checks that have fallen due.
 *
 * <p>Ticks frequently and does a little each time, rather than waking rarely and
 * doing everything: marketplace calls are rate-limited and the quota is shared
 * across tenants, so a burst of a thousand searches would throttle every client
 * at once. Each tick takes a bounded batch of the most overdue monitors.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonitorScheduler {

    private final MonitorService monitorService;
    private final MonitorHealthNotifier healthNotifier;

    @Value("${app.monitors.enabled:true}")
    private boolean enabled;

    /** Most monitors to run per tick — the throttle on marketplace spend. */
    @Value("${app.monitors.batch-size:10}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${app.monitors.tick-ms:300000}", initialDelay = 60_000)
    public void runDueMonitors() {
        if (!enabled) {
            return;
        }
        List<Monitor> due = monitorService.dueNow(batchSize);
        if (due.isEmpty()) {
            return;
        }
        log.info("Monitor sweep: {} due (batch limit {})", due.size(), batchSize);

        int ok = 0;
        int failed = 0;
        for (Monitor monitor : due) {
            // Run as the owning tenant so every tenant-scoped check inside the
            // search behaves exactly as it would for that client, rather than
            // relying on an empty context meaning "unrestricted".
            TenantContext.set(monitor.getTenantId(), false);
            // Captured before the run overwrites it: the notification fires on
            // the change of state, not on every failed run.
            String previousStatus = monitor.getLastStatus();
            try {
                monitorService.runNow(monitor);
                if ("FAILED".equals(monitor.getLastStatus())) {
                    failed++;
                } else {
                    ok++;
                }
                // Never let a notification problem mark a good run as failed.
                try {
                    healthNotifier.afterRun(monitor, previousStatus);
                } catch (Exception e) {
                    log.warn("Monitor {} health notification failed: {}",
                            monitor.getId(), e.getMessage());
                }
            } catch (Exception e) {
                // runNow records its own failures; this catches anything beyond
                // them so one bad monitor cannot end the sweep for the rest.
                failed++;
                log.error("Monitor {} aborted the run: {}", monitor.getId(), e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
        log.info("Monitor sweep finished: {} ok, {} failed", ok, failed);
    }
}
