package com.priceintel.backend.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.priceintel.backend.service.SubscriptionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Applies scheduled subscription changes. Currently: downgrades that were
 * deferred to the end of the current billing period.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionScheduler {

    private final SubscriptionService subscriptionService;
    private final com.priceintel.backend.service.impl.SubscriptionReminderService reminderService;

    /**
     * Runs daily at 02:00 (server time). Also runs ~1 minute after startup so a
     * long-overdue downgrade is picked up promptly after a restart.
     */
    @Scheduled(cron = "${app.scheduler.downgrade-cron:0 0 2 * * *}")
    public void applyScheduledDowngrades() {
        try {
            int applied = subscriptionService.runDueScheduledDowngrades();
            if (applied > 0) {
                log.info("Scheduler: applied {} scheduled downgrade(s)", applied);
            }
        } catch (Exception e) {
            log.error("Scheduler: scheduled-downgrade run failed: {}", e.getMessage(), e);
        }
    }

    /** Startup sweep so overdue changes are not stuck until the next 02:00. */
    @Scheduled(initialDelay = 60_000, fixedDelay = Long.MAX_VALUE)
    public void applyOnStartup() {
        applyScheduledDowngrades();
    }

    /**
     * Daily at 00:15: expire companies whose paid-through date plus grace has
     * passed, and move finished trials that are paid past to ACTIVE.
     *
     * <p>Known gap, not closed here: a scheduled downgrade takes effect on the
     * paid-through date and applyPlanNow grants it a fresh period without a
     * payment, so a company that downgrades never reaches expiry.</p>
     */
    @Scheduled(cron = "${app.scheduler.expiry-cron:0 15 0 * * *}")
    public void expireLapsedSubscriptions() {
        try {
            subscriptionService.runExpirySweep();
        } catch (Exception e) {
            log.error("Scheduler: expiry sweep failed: {}", e.getMessage(), e);
        }
    }

    /** Also once shortly after startup, so a server that was down at 00:15 catches up. */
    @Scheduled(initialDelay = 90_000, fixedDelay = Long.MAX_VALUE)
    public void expireOnStartup() {
        expireLapsedSubscriptions();
    }

    /**
     * Daily at 09:00: expiry reminders (7 days and 1 day before, by default).
     * Morning rather than midnight so they land when someone can act on them.
     */
    @Scheduled(cron = "${app.scheduler.reminder-cron:0 0 9 * * *}")
    public void sendExpiryReminders() {
        try {
            reminderService.sendDueReminders();
        } catch (Exception e) {
            log.error("Scheduler: expiry reminders failed: {}", e.getMessage(), e);
        }
    }

    /** After startup too; what was already sent is recorded, so nothing repeats. */
    @Scheduled(initialDelay = 120_000, fixedDelay = Long.MAX_VALUE)
    public void remindOnStartup() {
        sendExpiryReminders();
    }
}
