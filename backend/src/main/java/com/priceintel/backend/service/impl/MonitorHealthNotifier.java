package com.priceintel.backend.service.impl;

import org.springframework.stereotype.Service;

import com.priceintel.backend.constants.NotificationType;
import com.priceintel.backend.entity.Monitor;
import com.priceintel.backend.entity.Product;
import com.priceintel.backend.repository.ProductRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Tells the account when a scheduled check starts failing, and when it starts
 * working again.
 *
 * <p>Without this a monitor can fail silently for weeks: the row still reads
 * "Active", the last-run time still moves, and only the server log knows. The
 * screen looks healthiest exactly when it is least trustworthy.</p>
 *
 * <p>Notifies on the <em>transition</em>, never on every failed run. A daily
 * monitor against an unauthorised marketplace fails forever; announcing that
 * every day trains the user to ignore the bell, and the one notification that
 * matters is the first.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorHealthNotifier {

    private static final String FAILED = "FAILED";

    private final TenantNotifier notifier;
    private final ProductRepository productRepo;

    /**
     * Compares the status before and after a run and notifies if it changed.
     *
     * @param previousStatus the monitor's {@code lastStatus} before this run —
     *                       null the very first time, which counts as healthy so
     *                       a first-run failure is still announced
     */
    public void afterRun(Monitor monitor, String previousStatus) {
        boolean wasFailing = FAILED.equals(previousStatus);
        boolean isFailing = FAILED.equals(monitor.getLastStatus());

        if (isFailing == wasFailing) {
            return;
        }
        String product = productTitle(monitor);
        if (isFailing) {
            notifier.notifyAndEmail(monitor.getTenantId(), NotificationType.MONITOR_FAILED,
                    "Monitor failed: " + product,
                    reason(monitor) + " The next attempt is in "
                            + humanInterval(monitor.getIntervalMinutes())
                            + ". Competitor prices for this product will not update until it succeeds.",
                    link(monitor));
            log.warn("Monitor {} (product {}) started failing: {}",
                    monitor.getId(), monitor.getProductId(), monitor.getLastMessage());
        } else {
            // Recovery goes to the bell only. Someone who got the failure mail
            // wants to know it cleared, but not badly enough for a second mail.
            notifier.notify(monitor.getTenantId(), NotificationType.MONITOR_RECOVERED,
                    "Monitor recovered: " + product,
                    "The scheduled check is working again and found "
                            + (monitor.getLastResultCount() == null ? 0 : monitor.getLastResultCount())
                            + " listing(s).",
                    link(monitor));
            log.info("Monitor {} (product {}) recovered", monitor.getId(), monitor.getProductId());
        }
    }

    private String reason(Monitor monitor) {
        String message = monitor.getLastMessage();
        return message == null || message.isBlank()
                ? "The scheduled competitor check did not complete."
                : "The scheduled competitor check did not complete: " + message;
    }

    private String productTitle(Monitor monitor) {
        return productRepo.findById(monitor.getProductId())
                .map(Product::getTitle)
                .orElse("product #" + monitor.getProductId());
    }

    private String link(Monitor monitor) {
        return "/products/" + monitor.getProductId();
    }

    private String humanInterval(int minutes) {
        if (minutes % 1440 == 0) {
            int days = minutes / 1440;
            return days == 1 ? "a day" : days + " days";
        }
        if (minutes % 60 == 0) {
            int hours = minutes / 60;
            return hours == 1 ? "an hour" : hours + " hours";
        }
        return minutes + " minutes";
    }
}
