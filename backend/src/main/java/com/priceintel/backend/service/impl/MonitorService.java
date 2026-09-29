package com.priceintel.backend.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.priceintel.backend.dto.request.MonitorRequest;
import com.priceintel.backend.dto.response.MonitorResponse;
import com.priceintel.backend.entity.Monitor;
import com.priceintel.backend.entity.Product;
import com.priceintel.backend.entity.SubscriptionPlan;
import com.priceintel.backend.entity.Tenant;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.exception.DuplicateResourceException;
import com.priceintel.backend.exception.ResourceNotFoundException;
import com.priceintel.backend.marketplace.Marketplace;
import com.priceintel.backend.repository.MonitorRepository;
import com.priceintel.backend.repository.ProductRepository;
import com.priceintel.backend.repository.SubscriptionPlanRepository;
import com.priceintel.backend.repository.TenantRepository;
import com.priceintel.backend.security.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages scheduled competitor checks and runs the ones that are due.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorService {

    private final MonitorRepository monitorRepo;
    private final ProductRepository productRepo;
    private final TenantRepository tenantRepo;
    private final SubscriptionPlanRepository planRepo;
    private final SearchPipelineService searchPipeline;
    private final SubscriptionAccessService accessService;

    /** Used when a tenant has no plan, or the plan sets no floor. */
    private static final int DEFAULT_MIN_INTERVAL_MINUTES = 1440;

    // ---------- CRUD ----------

    @Transactional(readOnly = true)
    public List<MonitorResponse> list(Long productId, Boolean enabled) {
        Long tenantId = TenantContext.scopeOrAllForSuperAdmin();
        Integer floor = minIntervalFor(TenantContext.getTenantId());
        return monitorRepo.findForList(tenantId, productId, enabled).stream()
                .map(m -> toResponse(m, floor)).toList();
    }

    @Transactional
    public MonitorResponse create(MonitorRequest request) {
        if (request.getProductId() == null) {
            throw new BadRequestException("productId is required");
        }
        Product product = tenantProductOrThrow(request.getProductId());
        monitorRepo.findByProductId(product.getId()).ifPresent(m -> {
            throw new DuplicateResourceException(
                    "This product is already monitored. Edit the existing monitor instead.");
        });

        int floor = minIntervalFor(product.getTenantId());
        int interval = request.getIntervalMinutes() != null
                ? request.getIntervalMinutes() : floor;
        assertIntervalAllowed(interval, floor);

        Monitor monitor = Monitor.builder()
                .tenantId(product.getTenantId())
                .productId(product.getId())
                .markets(joinMarkets(request.getMarkets()))
                .maxResults(request.getMaxResults() != null ? request.getMaxResults() : 5)
                .intervalMinutes(interval)
                .enabled(request.getEnabled() == null || request.getEnabled())
                // Due immediately, so enabling a monitor produces data on the
                // next tick rather than one full interval from now.
                .nextRunAt(Instant.now())
                .build();
        monitor = monitorRepo.save(monitor);
        log.info("Monitor {} created for product {} every {} min",
                monitor.getId(), product.getId(), interval);
        return toResponse(monitor, floor);
    }

    @Transactional
    public MonitorResponse update(Long id, MonitorRequest request) {
        Monitor monitor = tenantMonitorOrThrow(id);
        int floor = minIntervalFor(monitor.getTenantId());

        if (request.getIntervalMinutes() != null) {
            assertIntervalAllowed(request.getIntervalMinutes(), floor);
            monitor.setIntervalMinutes(request.getIntervalMinutes());
            // Re-base the schedule on the new cadence rather than waiting out
            // whatever the old one had queued up.
            //
            // A monitor that has never run stays due now: editing it before its
            // first check should not push that check a whole interval into the
            // future, which on a daily cadence means a day of silence from
            // something the user just set up.
            monitor.setNextRunAt(monitor.getLastRunAt() == null
                    ? Instant.now()
                    : nextRunFrom(monitor.getLastRunAt(), request.getIntervalMinutes()));
        }
        if (request.getMarkets() != null) {
            monitor.setMarkets(joinMarkets(request.getMarkets()));
        }
        if (request.getMaxResults() != null) {
            monitor.setMaxResults(request.getMaxResults());
        }
        if (request.getEnabled() != null) {
            monitor.setEnabled(request.getEnabled());
            if (request.getEnabled() && monitor.getNextRunAt() == null) {
                monitor.setNextRunAt(Instant.now());
            }
        }
        return toResponse(monitorRepo.save(monitor), floor);
    }

    @Transactional
    public void delete(Long id) {
        monitorRepo.delete(tenantMonitorOrThrow(id));
    }

    // ---------- execution ----------

    /**
     * Runs one monitor and records the outcome.
     *
     * <p>The search may reuse a cached fetch, but only one younger than this
     * monitor's own interval — otherwise an hourly monitor would keep reporting
     * success off two-day-old data. Two monitors on the same cadence still share
     * a single marketplace call.</p>
     *
     * <p>Failures are recorded on the monitor rather than thrown, so one broken
     * product cannot stop the rest of the sweep.</p>
     *
     * <p>Deliberately <em>not</em> transactional. The search runs in its own
     * transaction; when it throws, that transaction is already marked
     * rollback-only, and a shared outer transaction would take the status write
     * down with it — the commit failed, "FAILED" was never stored, and the row
     * still read as never-run. The monitor then came due again on the next tick
     * and retried every five minutes instead of on its own interval. Recording
     * the outcome in its own transaction is what makes a failure durable.</p>
     */
    public void runNow(Monitor monitor) {
        Instant startedAt = Instant.now();
        try {
            var job = searchPipeline.runSearch(
                    monitor.getProductId(),
                    splitMarkets(monitor.getMarkets()),
                    monitor.getMaxResults(),
                    false,
                    Duration.ofMinutes(monitor.getIntervalMinutes()));
            monitor.setLastResultCount(job.getResultCount());
            monitor.setLastStatus("COMPLETED");
            monitor.setLastMessage(job.getNote());
        } catch (Exception e) {
            monitor.setLastStatus("FAILED");
            monitor.setLastMessage(truncate(e.getMessage()));
            log.warn("Monitor {} (product {}) failed: {}",
                    monitor.getId(), monitor.getProductId(), e.getMessage());
        }
        monitor.setLastRunAt(startedAt);
        monitor.setNextRunAt(nextRunFrom(startedAt, monitor.getIntervalMinutes()));
        monitorRepo.save(monitor);
    }

    /**
     * The listings this monitor's most recent run returned.
     *
     * <p>The Monitors screen says "5 listings last run" while the product's
     * Competitors tab shows everything ever found — 14 for the same product.
     * Both are correct and neither answers "which five". This does.</p>
     *
     * <p>Empty when the monitor has never run. A run that genuinely found
     * nothing also returns empty, which is the honest answer.</p>
     */
    @Transactional(readOnly = true)
    public List<com.priceintel.backend.dto.response.CompetitorListingResponse> lastRunListings(Long id) {
        Monitor monitor = tenantMonitorOrThrow(id);
        if (monitor.getLastRunAt() == null) {
            return List.of();
        }
        return searchPipeline.listingsSeenSince(monitor.getProductId(), monitor.getLastRunAt());
    }

    @Transactional(readOnly = true)
    public List<Monitor> dueNow(int limit) {
        // Expired and suspended companies' monitors are paused: they spend
        // marketplace and AI quota the company is no longer paying for. They
        // resume by themselves on renewal, since they simply come due again.
        return monitorRepo.findDueForFullAccess(Instant.now(),
                SubscriptionAccessService.lapseCutoff(java.time.LocalDate.now(), accessService.getGraceDays()),
                org.springframework.data.domain.PageRequest.of(0, limit));
    }

    // ---------- helpers ----------

    /** The fastest cadence a tenant may choose, from its subscription plan. */
    @Transactional(readOnly = true)
    public Integer minIntervalFor(Long tenantId) {
        if (tenantId == null) {
            return DEFAULT_MIN_INTERVAL_MINUTES;
        }
        return tenantRepo.findById(tenantId)
                .map(Tenant::getSubscriptionPlan)
                .flatMap(planRepo::findByCodeIgnoreCase)
                .map(SubscriptionPlan::getMinIntervalMinutes)
                .filter(v -> v > 0)
                .orElse(DEFAULT_MIN_INTERVAL_MINUTES);
    }

    private void assertIntervalAllowed(int requested, int floor) {
        if (requested < floor) {
            throw new BadRequestException(
                    "Your plan allows a check every " + floor + " minutes at most often. "
                            + "Requested " + requested + ". Upgrade for a faster cadence.");
        }
    }

    private Instant nextRunFrom(Instant last, int intervalMinutes) {
        Instant base = last != null ? last : Instant.now();
        return base.plus(Duration.ofMinutes(intervalMinutes));
    }

    private Product tenantProductOrThrow(Long productId) {
        Product product = productRepo.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        Long tenantId = TenantContext.scopeOrAllForSuperAdmin();
        if (tenantId != null && !tenantId.equals(product.getTenantId())) {
            throw new ResourceNotFoundException("Product not found: " + productId);
        }
        return product;
    }

    private Monitor tenantMonitorOrThrow(Long id) {
        Monitor monitor = monitorRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Monitor not found: " + id));
        Long tenantId = TenantContext.scopeOrAllForSuperAdmin();
        if (tenantId != null && !tenantId.equals(monitor.getTenantId())) {
            throw new ResourceNotFoundException("Monitor not found: " + id);
        }
        return monitor;
    }

    private String joinMarkets(List<String> markets) {
        if (markets == null || markets.isEmpty()) {
            return null;
        }
        // Validate now so a typo fails at save time, not silently every tick.
        return String.join(",", markets.stream()
                .map(m -> {
                    try {
                        return Marketplace.valueOf(m.trim().toUpperCase()).name();
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Unknown marketplace: " + m);
                    }
                })
                .toList());
    }

    private List<String> splitMarkets(String markets) {
        return markets == null || markets.isBlank()
                ? null : Arrays.asList(markets.split(","));
    }

    private MonitorResponse toResponse(Monitor m, Integer floor) {
        return MonitorResponse.builder()
                .id(m.getId())
                .productId(m.getProductId())
                .productTitle(productRepo.findById(m.getProductId())
                        .map(Product::getTitle).orElse(null))
                .markets(splitMarkets(m.getMarkets()))
                .maxResults(m.getMaxResults())
                .intervalMinutes(m.getIntervalMinutes())
                .enabled(m.isEnabled())
                .lastRunAt(m.getLastRunAt())
                .nextRunAt(m.getNextRunAt())
                .lastResultCount(m.getLastResultCount())
                .lastStatus(m.getLastStatus())
                .lastMessage(m.getLastMessage())
                .minIntervalMinutes(floor)
                .build();
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 480 ? s.substring(0, 480) : s;
    }
}
