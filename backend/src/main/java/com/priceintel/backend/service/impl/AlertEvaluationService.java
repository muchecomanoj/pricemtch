package com.priceintel.backend.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.priceintel.backend.constants.AlertCondition;
import com.priceintel.backend.constants.NotificationType;
import com.priceintel.backend.dto.response.MarketPricesResponse;
import com.priceintel.backend.dto.response.ProfitabilityResponse;
import com.priceintel.backend.entity.AlertRule;
import com.priceintel.backend.entity.CompetitorListing;
import com.priceintel.backend.entity.Product;
import com.priceintel.backend.repository.AlertRuleRepository;
import com.priceintel.backend.repository.CompetitorListingRepository;
import com.priceintel.backend.repository.ProductRepository;
import com.priceintel.backend.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The alert-firing engine (FR-ALERT-002). Evaluates every active {@link AlertRule}
 * against the current stored competitor/price/margin data and, when a condition
 * is met, raises a notification (the bell). Runs on a schedule and can also be
 * triggered manually. A per-rule cooldown prevents repeat spam.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertEvaluationService {

    private final AlertRuleRepository alertRepo;
    private final ProductRepository productRepo;
    private final CompetitorListingRepository listingRepo;
    private final SearchPipelineService searchService;
    private final CostProfileService costService;
    private final NotificationService notificationService;
    private final NotificationChannelService channelService;
    private final TenantNotifier tenantNotifier;
    private final com.priceintel.backend.repository.TenantRepository tenantRepo;
    private final SubscriptionAccessService accessService;

    private static final Duration COOLDOWN = Duration.ofHours(1);

    /**
     * Whether this rule asks for chat delivery.
     *
     * <p>A rule with no channels set keeps its original behaviour — in-app only
     * — so enabling Slack for the tenant does not retroactively make every
     * existing rule noisy.</p>
     */
    private boolean wantsChatDelivery(com.priceintel.backend.entity.AlertRule rule) {
        return wantsChannel(rule, "SLACK") || wantsChannel(rule, "TEAMS");
    }

    /** Whether this rule asks for a given delivery channel. */
    private boolean wantsChannel(com.priceintel.backend.entity.AlertRule rule, String channel) {
        String channels = rule.getChannels();
        return channels != null && channels.toUpperCase().contains(channel);
    }

    /** Hourly evaluation of all active rules. */
    @Scheduled(cron = "0 0 * * * *")
    public void scheduled() {
        int fired = evaluateAll();
        if (fired > 0) {
            log.info("Alert engine: {} alert(s) fired", fired);
        }
    }

    /** Evaluate every active rule; returns how many fired a notification. */
    @Transactional
    public int evaluateAll() {
        int fired = 0;
        // Expired and suspended companies' alerts are paused, like their
        // monitors: no emails or Slack messages for a plan that has lapsed.
        java.util.Set<Long> fullAccess = new java.util.HashSet<>(tenantRepo.findFullAccessTenantIds(
                SubscriptionAccessService.lapseCutoff(java.time.LocalDate.now(), accessService.getGraceDays())));
        for (AlertRule rule : alertRepo.findByActiveTrue()) {
            if (rule.getTenantId() != null && !fullAccess.contains(rule.getTenantId())) {
                continue;
            }
            try {
                if (evaluate(rule)) {
                    fired++;
                }
            } catch (RuntimeException e) {
                log.warn("Alert rule {} evaluation failed: {}", rule.getId(), e.getMessage());
            }
        }
        return fired;
    }

    /** Evaluate one rule; fires a notification if the condition is met (+ cooldown). */
    @Transactional
    public boolean evaluate(AlertRule rule) {
        Product product = productRepo.findById(rule.getProductId()).orElse(null);
        if (product == null) {
            return false;
        }
        MarketPricesResponse m = searchService.marketPrices(rule.getProductId(), false);
        BigDecimal thr = rule.getThresholdValue() != null
                ? rule.getThresholdValue() : defaultThreshold(rule.getCondition());
        boolean percent = !"ABSOLUTE".equalsIgnoreCase(rule.getThresholdType());

        String message = null;

        switch (rule.getCondition()) {
            case PRICE_DROP -> {
                BigDecimal cur = m.getMedian();
                BigDecimal base = rule.getLastObservedValue();
                if (cur != null && base != null && cur.compareTo(base) < 0
                        && metric(base.subtract(cur), base, percent).compareTo(thr) >= 0) {
                    message = "Competitor price dropped to " + money(cur) + " (was " + money(base) + ")";
                }
                rule.setLastObservedValue(cur);
            }
            case PRICE_INCREASE -> {
                BigDecimal cur = m.getMedian();
                BigDecimal base = rule.getLastObservedValue();
                if (cur != null && base != null && cur.compareTo(base) > 0
                        && metric(cur.subtract(base), base, percent).compareTo(thr) >= 0) {
                    message = "Competitor price rose to " + money(cur) + " (was " + money(base) + ")";
                }
                rule.setLastObservedValue(cur);
            }
            case COMPETITOR_CHANGE -> {
                BigDecimal cur = m.getMedian();
                BigDecimal base = rule.getLastObservedValue();
                if (cur != null && base != null && cur.compareTo(base) != 0
                        && metric(cur.subtract(base).abs(), base, percent).compareTo(thr) >= 0) {
                    message = "Competitor median changed to " + money(cur) + " (was " + money(base) + ")";
                }
                rule.setLastObservedValue(cur);
            }
            case NEW_SELLER -> {
                int cur = m.getCompetitorCount();
                Integer base = rule.getLastListingCount();
                if (base != null && cur > base) {
                    message = (cur - base) + " new competitor listing(s) — now " + cur + " total";
                }
                rule.setLastListingCount(cur);
            }
            case OUT_OF_STOCK -> {
                boolean any = listings(rule.getProductId()).stream()
                        .anyMatch(l -> "OUT_OF_STOCK".equalsIgnoreCase(l.getAvailability()));
                if (any) {
                    message = "A competitor is out of stock — an opportunity to capture demand";
                }
            }
            case MARGIN_BREACH -> {
                BigDecimal margin = marginOf(rule.getProductId());
                if (margin != null && margin.compareTo(thr) < 0) {
                    message = "Margin " + margin.stripTrailingZeros().toPlainString()
                            + "% is below your " + thr.stripTrailingZeros().toPlainString() + "% threshold";
                }
            }
            case PRICE_GAP -> {
                BigDecimal our = product.getOurPrice();
                BigDecimal lowest = m.getLowest();
                if (our != null && lowest != null && lowest.signum() > 0) {
                    BigDecimal gapPct = our.subtract(lowest).multiply(BigDecimal.valueOf(100))
                            .divide(lowest, 2, RoundingMode.HALF_UP);
                    if (gapPct.compareTo(thr) >= 0) {
                        message = "Your price " + money(our) + " is " + gapPct
                                + "% above the lowest competitor (" + money(lowest) + ")";
                    }
                }
            }
            case DATA_STALE -> {
                Instant latest = listings(rule.getProductId()).stream()
                        .map(CompetitorListing::getSourceTimestamp).filter(java.util.Objects::nonNull)
                        .max(Instant::compareTo).orElse(null);
                int days = thr != null ? thr.intValue() : 7;
                if (latest != null && latest.isBefore(Instant.now().minus(days, ChronoUnit.DAYS))) {
                    message = "Competitor data is stale — last refreshed " + latest;
                }
            }
            default -> { /* nothing */ }
        }

        boolean fired = false;
        if (message != null && cooldownElapsed(rule) && rule.getTenantId() != null) {
            String title = "Price alert: " + product.getTitle();
            String link = "/products/" + rule.getProductId();

            // The bell always gets it — it is the record that the alert fired,
            // and it must not depend on a chat provider being reachable.
            notificationService.notifyTenant(rule.getTenantId(), NotificationType.ALERT_TRIGGERED,
                    title, message, link);

            // Email and chat are best-effort on top: a delivery failure is
            // logged and never loses the alert, which is already recorded.
            if (wantsChannel(rule, "EMAIL")) {
                tenantNotifier.email(rule.getTenantId(), title, message);
            }
            if (wantsChatDelivery(rule)) {
                channelService.deliver(rule.getTenantId(), title, message, link);
            }
            rule.setLastFiredAt(Instant.now());
            rule.setTriggerCount(rule.getTriggerCount() + 1);
            fired = true;
            log.info("Alert fired for rule {} (product {}): {}", rule.getId(), rule.getProductId(), message);
        }
        alertRepo.save(rule);
        return fired;
    }

    // ---------- helpers ----------

    private boolean cooldownElapsed(AlertRule rule) {
        return rule.getLastFiredAt() == null
                || rule.getLastFiredAt().isBefore(Instant.now().minus(COOLDOWN));
    }

    private BigDecimal metric(BigDecimal delta, BigDecimal base, boolean percent) {
        if (!percent) {
            return delta;
        }
        return base.signum() == 0 ? BigDecimal.ZERO
                : delta.multiply(BigDecimal.valueOf(100)).divide(base, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal marginOf(Long productId) {
        try {
            ProfitabilityResponse pr = costService.computeProfitability(productId, null);
            return pr.isCostProfileConfigured() ? pr.getContributionMarginPct() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * The product's live candidates, rejected ones excluded.
     *
     * <p>Otherwise a listing someone has already dismissed could still raise an
     * out-of-stock alert — a notification about a product that is not theirs,
     * which they cannot act on and cannot stop except by deleting the rule.</p>
     */
    private List<CompetitorListing> listings(Long productId) {
        return listingRepo.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .filter(l -> l.getMatchStatus()
                        != com.priceintel.backend.constants.MatchStatus.REJECTED)
                .toList();
    }

    private BigDecimal defaultThreshold(AlertCondition c) {
        return switch (c) {
            case MARGIN_BREACH -> BigDecimal.valueOf(15);
            case DATA_STALE -> BigDecimal.valueOf(7);
            case NEW_SELLER, OUT_OF_STOCK -> BigDecimal.ZERO;
            default -> BigDecimal.valueOf(5);
        };
    }

    private String money(BigDecimal v) {
        return v == null ? "—" : "$" + v.toPlainString();
    }
}
