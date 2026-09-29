package com.priceintel.backend.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.priceintel.backend.constants.AccessType;
import com.priceintel.backend.constants.BillingCycle;
import com.priceintel.backend.constants.EmailTemplateKey;
import com.priceintel.backend.constants.NotificationType;
import com.priceintel.backend.constants.SubscriptionStatus;
import com.priceintel.backend.constants.TenantStatus;
import com.priceintel.backend.constants.UserStatus;
import com.priceintel.backend.entity.SubscriptionPlan;
import com.priceintel.backend.entity.SubscriptionReminder;
import com.priceintel.backend.entity.Tenant;
import com.priceintel.backend.entity.User;
import com.priceintel.backend.repository.SubscriptionPlanRepository;
import com.priceintel.backend.repository.SubscriptionReminderRepository;
import com.priceintel.backend.repository.TenantRepository;
import com.priceintel.backend.repository.UserRepository;
import com.priceintel.backend.service.MailService;
import com.priceintel.backend.service.NotificationService;

import lombok.extern.slf4j.Slf4j;

/**
 * Tells a company its plan is about to end, and again when it has.
 *
 * <h3>Once per reminder, per period</h3>
 *
 * <p>Every send is recorded against the company, the paid-through date and the
 * kind of reminder. The daily job runs again after each restart, and that
 * record is what stops it repeating itself. Renewing moves the paid-through
 * date, so the next period's reminders start afresh with nothing to reset.</p>
 *
 * <h3>Catching up rather than missing</h3>
 *
 * <p>Reminders are not "exactly 7 days before". If the server was down that
 * morning, the 7-day reminder goes out on day 5 instead of never — each day
 * the most urgent reminder that applies and has not been sent goes out, and
 * only one, so a company is never sent two on the same day.</p>
 */
@Slf4j
@Service
public class SubscriptionReminderService {

    public static final String EXPIRED = "EXPIRED";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionReminderRepository reminderRepository;
    private final MailService mailService;
    private final NotificationService notificationService;
    private final SubscriptionAccessService accessService;
    private final List<Integer> reminderDays;
    private final String frontendUrl;

    public SubscriptionReminderService(TenantRepository tenantRepository,
                                       UserRepository userRepository,
                                       SubscriptionPlanRepository planRepository,
                                       SubscriptionReminderRepository reminderRepository,
                                       MailService mailService,
                                       NotificationService notificationService,
                                       SubscriptionAccessService accessService,
                                       @Value("${app.subscription.reminder-days:7,1}") String reminderDays,
                                       @Value("${app.frontend-url}") String frontendUrl) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.planRepository = planRepository;
        this.reminderRepository = reminderRepository;
        this.mailService = mailService;
        this.notificationService = notificationService;
        this.accessService = accessService;
        this.reminderDays = parseDays(reminderDays);
        this.frontendUrl = frontendUrl;
    }

    /** "7,1" → [1, 7]: positive, distinct, most urgent first. */
    static List<Integer> parseDays(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(Integer::parseInt).filter(d -> d > 0)
                .distinct().sorted().toList();
    }

    /**
     * The reminder due for this many days left: the most urgent one that
     * applies. 5 days left with [1, 7] → 7; 1 or 0 days left → 1; 10 → none.
     */
    static Integer reminderFor(long daysLeft, List<Integer> days) {
        if (daysLeft < 0) {
            return null;
        }
        for (Integer d : days) {
            if (daysLeft <= d) {
                return d;
            }
        }
        return null;
    }

    static String endsIn(long daysLeft) {
        if (daysLeft <= 0) {
            return "today";
        }
        return daysLeft == 1 ? "tomorrow" : "in " + daysLeft + " days";
    }

    // ---------- before expiry ----------

    @Transactional
    public int sendDueReminders() {
        return sendDueReminders(LocalDate.now());
    }

    @Transactional
    public int sendDueReminders(LocalDate today) {
        if (reminderDays.isEmpty()) {
            return 0;
        }
        int sent = 0;
        for (Tenant tenant : tenantRepository.findByStatusAndSubscriptionStatusIn(TenantStatus.ACTIVE,
                List.of(SubscriptionStatus.TRIAL, SubscriptionStatus.ACTIVE))) {
            LocalDate paid = SubscriptionAccessService.paidThrough(tenant);
            if (paid == null) {
                continue;   // open-ended: nothing ends
            }
            long daysLeft = ChronoUnit.DAYS.between(today, paid);
            Integer due = reminderFor(daysLeft, reminderDays);
            if (due == null) {
                continue;
            }
            String kind = "BEFORE_" + due;
            if (reminderRepository.existsByTenantIdAndPaidThroughAndKind(tenant.getId(), paid, kind)) {
                continue;
            }
            // A more urgent one already went out (e.g. the server was down for
            // a week): an older, gentler reminder now would only confuse.
            if (moreUrgentAlreadySent(tenant.getId(), paid, due)) {
                continue;
            }
            Map<String, Object> vars = vars(tenant, paid);
            vars.put("daysLeft", daysLeft);
            vars.put("endsIn", endsIn(daysLeft));
            vars.put("graceDays", accessService.getGraceDays());
            vars.put("accessEndsOn", DATE.format(paid.plusDays(accessService.getGraceDays())));
            deliver(tenant, paid, kind, EmailTemplateKey.SUBSCRIPTION_EXPIRING, vars,
                    "Your plan ends " + endsIn(daysLeft),
                    "Your " + vars.get("planName") + " plan ends on " + vars.get("paidThrough")
                            + ". Renew to keep full access.");
            sent++;
        }
        if (sent > 0) {
            log.info("Expiry reminders: {} sent", sent);
        }
        return sent;
    }

    private boolean moreUrgentAlreadySent(Long tenantId, LocalDate paid, int due) {
        for (Integer d : reminderDays) {
            if (d < due && reminderRepository.existsByTenantIdAndPaidThroughAndKind(tenantId, paid, "BEFORE_" + d)) {
                return true;
            }
        }
        return false;
    }

    // ---------- at expiry ----------

    /**
     * Called by the nightly sweep as it marks a company EXPIRED. Returns false
     * if this period's notice had already gone out.
     */
    @Transactional
    public boolean sendExpiredNotice(Tenant tenant) {
        LocalDate paid = SubscriptionAccessService.paidThrough(tenant);
        if (paid == null || reminderRepository.existsByTenantIdAndPaidThroughAndKind(tenant.getId(), paid, EXPIRED)) {
            return false;
        }
        Map<String, Object> vars = vars(tenant, paid);
        deliver(tenant, paid, EXPIRED, EmailTemplateKey.SUBSCRIPTION_EXPIRED, vars,
                "Your plan has expired",
                "Your " + vars.get("planName") + " plan ended on " + vars.get("paidThrough")
                        + ". Your account is read-only until you renew.");
        return true;
    }

    // ---------- shared ----------

    private Map<String, Object> vars(Tenant tenant, LocalDate paid) {
        Map<String, Object> v = new LinkedHashMap<>();
        SubscriptionPlan plan = tenant.getSubscriptionPlan() == null ? null
                : planRepository.findByCodeIgnoreCase(tenant.getSubscriptionPlan()).orElse(null);
        BillingCycle cycle = tenant.getBillingCycle() != null ? tenant.getBillingCycle() : BillingCycle.MONTHLY;
        v.put("companyName", tenant.getCompanyName());
        v.put("planName", plan != null ? plan.getName() : String.valueOf(tenant.getSubscriptionPlan()));
        v.put("billingCycle", cycle == BillingCycle.YEARLY ? "yearly" : "monthly");
        BigDecimal price = plan == null ? null
                : (cycle == BillingCycle.YEARLY ? plan.computeYearlyPrice() : plan.getPrice());
        String currency = tenant.getCurrency() != null ? tenant.getCurrency() : "USD";
        v.put("amount", price == null || price.signum() <= 0 ? "free" : currency + " " + price);
        v.put("paidThrough", DATE.format(paid));
        v.put("renewLink", frontendUrl + "/my-subscription");
        return v;
    }

    /** Records first, then sends — a failed send must not become a retry storm. */
    private void deliver(Tenant tenant, LocalDate paid, String kind, EmailTemplateKey key,
                         Map<String, Object> vars, String title, String inAppMessage) {
        Map<String, String> recipients = recipients(tenant);
        reminderRepository.save(SubscriptionReminder.builder()
                .tenantId(tenant.getId()).paidThrough(paid).kind(kind)
                .recipients(truncate(String.join(", ", recipients.keySet()), 1000))
                .build());
        recipients.forEach((email, name) ->
                mailService.sendSubscriptionNotice(key, email, name, new LinkedHashMap<>(vars)));
        notificationService.notifyTenant(tenant.getId(), NotificationType.GENERAL, title, inAppMessage,
                "/my-subscription");
        log.info("Subscription {} notice for tenant {} ({} through {}) → {}",
                kind, tenant.getId(), tenant.getSubscriptionPlan(), paid, recipients.keySet());
    }

    /**
     * The company's registered email and every active admin — the people who
     * can pay. Analysts and viewers cannot renew, so they are not told to.
     */
    Map<String, String> recipients(Tenant tenant) {
        Map<String, String> out = new LinkedHashMap<>();
        String contact = tenant.getContactPerson() != null && !tenant.getContactPerson().isBlank()
                ? tenant.getContactPerson() : tenant.getCompanyName();
        if (tenant.getCompanyEmail() != null && !tenant.getCompanyEmail().isBlank()) {
            out.put(tenant.getCompanyEmail().trim().toLowerCase(Locale.ROOT), contact);
        }
        List<User> admins = new ArrayList<>(userRepository.findByTenantId(tenant.getId()));
        admins.stream()
                .filter(u -> u.getAccessType() == AccessType.ADMIN && u.getStatus() == UserStatus.ACTIVE)
                .filter(u -> u.getEmail() != null && !u.getEmail().isBlank())
                .forEach(u -> out.putIfAbsent(u.getEmail().trim().toLowerCase(Locale.ROOT),
                        Objects.requireNonNullElse(u.getFullName(), contact)));
        return out;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
