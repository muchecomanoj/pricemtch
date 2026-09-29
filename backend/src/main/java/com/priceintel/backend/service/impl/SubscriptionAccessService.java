package com.priceintel.backend.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.priceintel.backend.constants.AccountAccess;
import com.priceintel.backend.constants.SubscriptionStatus;
import com.priceintel.backend.constants.TenantStatus;
import com.priceintel.backend.dto.response.AccountStatusResponse;
import com.priceintel.backend.entity.Tenant;
import com.priceintel.backend.repository.TenantRepository;

/**
 * Decides what a company may do today — the one rule behind login, every API
 * request, and the background jobs.
 *
 * <h3>The date that matters is the paid-through date</h3>
 *
 * <p>A company has two end dates. {@code trialEndDate} is when the free trial
 * stops; {@code subscriptionEndDate} is how far it has paid, and at sign-up it
 * is set to the trial end <em>plus</em> the first paid period. Patuli Infosys'
 * trial ended on 24 Aug 2026 but it paid for a year, to 24 Aug 2027. Judging
 * by the trial date would lock out a customer who has paid for eleven more
 * months, so access follows {@code subscriptionEndDate}, falling back to the
 * trial end only where no paid-through date exists.</p>
 *
 * <h3>Status is not enough on its own</h3>
 *
 * <p>Access is worked out from the dates at request time as well as from the
 * stored status, so a company whose date passed at midnight is restricted
 * straight away rather than whenever the nightly sweep next runs. The sweep
 * then makes the stored status agree, for the admin screens and the history.</p>
 */
@Service
public class SubscriptionAccessService {

    /** How long a decision is reused before the database is asked again. */
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final TenantRepository tenantRepository;
    private final int graceDays;
    private final Map<Long, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(AccountAccess access, Instant loadedAt) {
    }

    public SubscriptionAccessService(TenantRepository tenantRepository,
                                     @Value("${app.subscription.grace-days:3}") int graceDays) {
        this.tenantRepository = tenantRepository;
        this.graceDays = Math.max(0, graceDays);
    }

    public int getGraceDays() {
        return graceDays;
    }

    // ---------- the rule ----------

    /** The last day paid for; the trial end where nothing was paid; null if open-ended. */
    public static LocalDate paidThrough(Tenant t) {
        return t.getSubscriptionEndDate() != null ? t.getSubscriptionEndDate() : t.getTrialEndDate();
    }

    /**
     * The earliest paid-through date that still has full access today. Anything
     * earlier has run past its grace period. Used by queries that must exclude
     * lapsed companies without loading each one.
     */
    public static LocalDate lapseCutoff(LocalDate today, int graceDays) {
        return today.minusDays(graceDays);
    }

    /** Whether the paid-through date, plus grace, is behind us. */
    public static boolean isLapsed(Tenant t, LocalDate today, int graceDays) {
        LocalDate paid = paidThrough(t);
        return paid != null && paid.isBefore(lapseCutoff(today, graceDays));
    }

    public static AccountAccess accessFor(Tenant t, LocalDate today, int graceDays) {
        if (t == null || t.getStatus() != TenantStatus.ACTIVE) {
            // Suspended, inactive, removed, or not finished signing up.
            return AccountAccess.BLOCKED;
        }
        SubscriptionStatus ss = t.getSubscriptionStatus();
        if (ss == SubscriptionStatus.SUSPENDED || ss == SubscriptionStatus.CANCELLED) {
            return AccountAccess.BLOCKED;
        }
        if (ss == SubscriptionStatus.EXPIRED) {
            return AccountAccess.READ_ONLY;
        }
        return isLapsed(t, today, graceDays) ? AccountAccess.READ_ONLY : AccountAccess.FULL;
    }

    // ---------- lookups ----------

    /** Access for a tenant id; FULL for the platform owner, who has no tenant. */
    public AccountAccess accessForTenant(Long tenantId) {
        if (tenantId == null) {
            return AccountAccess.FULL;
        }
        Cached hit = cache.get(tenantId);
        if (hit != null && hit.loadedAt().plus(CACHE_TTL).isAfter(Instant.now())) {
            return hit.access();
        }
        AccountAccess access = accessFor(
                tenantRepository.findById(tenantId).orElse(null), LocalDate.now(), graceDays);
        cache.put(tenantId, new Cached(access, Instant.now()));
        return access;
    }

    public AccountAccess accessFor(Tenant tenant) {
        return accessFor(tenant, LocalDate.now(), graceDays);
    }

    /** Forget a tenant's cached decision — call after any change to its status or dates. */
    public void evict(Long tenantId) {
        if (tenantId != null) {
            cache.remove(tenantId);
        }
    }

    // ---------- what the screen is told ----------

    /** Standing for a user's company; null for the platform owner, who has none. */
    public AccountStatusResponse describeTenant(Long tenantId) {
        if (tenantId == null) {
            return null;
        }
        return describe(tenantRepository.findById(tenantId).orElse(null));
    }

    public AccountStatusResponse describe(Tenant t) {
        return describe(t, LocalDate.now(), graceDays);
    }

    public static AccountStatusResponse describe(Tenant t, LocalDate today, int graceDays) {
        AccountAccess access = accessFor(t, today, graceDays);
        if (t == null) {
            return AccountStatusResponse.builder().access(access)
                    .message("Your organization was not found.").build();
        }
        LocalDate paid = paidThrough(t);
        LocalDate accessEnds = paid == null ? null : paid.plusDays(graceDays);
        Integer graceLeft = null;
        if (access == AccountAccess.FULL && paid != null && paid.isBefore(today)) {
            graceLeft = (int) ChronoUnit.DAYS.between(today, accessEnds);
        }
        return AccountStatusResponse.builder()
                .access(access)
                .tenantStatus(t.getStatus())
                .subscriptionStatus(t.getSubscriptionStatus())
                .plan(t.getSubscriptionPlan())
                .paidThrough(paid)
                .accessEndsOn(accessEnds)
                .graceDaysLeft(graceLeft)
                .message(messageFor(t, access, paid, graceLeft))
                .build();
    }

    /** The sentence shown to the user — also the text of a refusal. */
    public static String messageFor(Tenant t, AccountAccess access, LocalDate paid, Integer graceLeft) {
        if (access == AccountAccess.BLOCKED) {
            return blockedReason(t);
        }
        if (access == AccountAccess.READ_ONLY) {
            return "Your plan expired" + (paid == null ? "" : " on " + paid)
                    + ". You can still view and export your data. Renew to search marketplaces, "
                    + "add products and run monitors again.";
        }
        if (graceLeft != null) {
            return "Your plan ended on " + paid + ". "
                    + (graceLeft <= 0 ? "Renew today"
                        : "Renew within " + graceLeft + (graceLeft == 1 ? " day" : " days"))
                    + " to keep full access.";
        }
        return null;
    }

    public static String blockedReason(Tenant t) {
        if (t == null) {
            return "Your organization was not found.";
        }
        if (t.getStatus() != null) {
            switch (t.getStatus()) {
                case PENDING_ACTIVATION:
                    return "Your account is not activated yet. Please use the activation link sent to your email.";
                case PENDING_PAYMENT:
                    return "Please complete your subscription payment to finish setting up your account.";
                case DELETED:
                    return "Your organization has been removed.";
                case SUSPENDED:
                    return "Your organization is suspended. Contact the platform administrator.";
                case INACTIVE:
                    return "Your organization is inactive. Contact the platform administrator.";
                default:
                    break;
            }
        }
        if (t.getSubscriptionStatus() == SubscriptionStatus.CANCELLED) {
            return "Your subscription has been cancelled. Contact the platform administrator.";
        }
        if (t.getSubscriptionStatus() == SubscriptionStatus.SUSPENDED) {
            return "Your subscription is suspended. Contact the platform administrator.";
        }
        return "Your organization cannot be accessed right now.";
    }
}
