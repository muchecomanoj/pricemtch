package com.priceintel.backend.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.priceintel.backend.constants.BillingCycle;
import com.priceintel.backend.constants.PaymentStatus;
import com.priceintel.backend.constants.PaymentType;
import com.priceintel.backend.constants.SubscriptionAction;
import com.priceintel.backend.constants.SubscriptionStatus;
import com.priceintel.backend.constants.TenantStatus;
import com.priceintel.backend.dto.request.PlanRequest;
import com.priceintel.backend.dto.response.PagedResponse;
import com.priceintel.backend.dto.response.PaymentResponse;
import com.priceintel.backend.dto.response.PlanChangeResponse;
import com.priceintel.backend.dto.response.PlanResponse;
import com.priceintel.backend.dto.response.SubscriptionHistoryResponse;
import com.priceintel.backend.dto.response.TenantResponse;
import com.priceintel.backend.entity.Payment;
import com.priceintel.backend.entity.SubscriptionHistory;
import com.priceintel.backend.entity.SubscriptionPlan;
import com.priceintel.backend.entity.Tenant;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.exception.DuplicateResourceException;
import com.priceintel.backend.exception.ResourceNotFoundException;
import com.priceintel.backend.mapper.SubscriptionMapper;
import com.priceintel.backend.mapper.TenantMapper;
import com.priceintel.backend.payment.StripeService;
import com.priceintel.backend.repository.PaymentRepository;
import com.priceintel.backend.repository.SubscriptionHistoryRepository;
import com.priceintel.backend.repository.SubscriptionPlanRepository;
import com.priceintel.backend.repository.TenantRepository;
import com.priceintel.backend.service.MailService;
import com.priceintel.backend.service.SubscriptionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private final TenantRepository tenantRepository;
    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionHistoryRepository historyRepository;
    private final PaymentRepository paymentRepository;
    private final SubscriptionMapper subscriptionMapper;
    private final TenantMapper tenantMapper;
    private final StripeService stripeService;
    private final MailService mailService;
    private final com.priceintel.backend.service.NotificationService notificationService;
    private final SubscriptionAccessService accessService;
    private final SubscriptionReminderService reminderService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /** Reference prefix so the Stripe webhook routes a payment to applyPaidPayment. */
    public static final String PAY_REF_PREFIX = "pay:";

    // ---------- plans ----------

    @Override
    @Transactional(readOnly = true)
    public List<PlanResponse> listPlans() {
        return planRepository.findAll(Sort.by("price")).stream().map(subscriptionMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlanResponse> listActivePlans() {
        return planRepository.findByActiveTrueOrderByPriceAsc().stream()
                .map(subscriptionMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public PlanResponse createPlan(PlanRequest r) {
        if (planRepository.existsByCodeIgnoreCase(r.getCode())) {
            throw new DuplicateResourceException("Plan code already exists: " + r.getCode());
        }
        SubscriptionPlan plan = SubscriptionPlan.builder()
                .code(r.getCode().toUpperCase()).name(r.getName()).description(r.getDescription())
                .price(r.getPrice() != null ? r.getPrice() : BigDecimal.ZERO)
                .currency(r.getCurrency())
                .billingCycleDays(r.getBillingCycleDays() != null ? r.getBillingCycleDays() : 30)
                .maxUsers(r.getMaxUsers() != null ? r.getMaxUsers() : 5)
                .trialDays(r.getTrialDays() != null ? r.getTrialDays() : 14)
                .yearlyPrice(r.getYearlyPrice())
                .yearlyDiscountPercent(r.getYearlyDiscountPercent() != null
                        ? r.getYearlyDiscountPercent() : SubscriptionPlan.DEFAULT_YEARLY_DISCOUNT_PERCENT)
                .features(r.getFeatures())
                .active(r.getActive() == null || r.getActive())
                .build();
        return subscriptionMapper.toResponse(planRepository.save(plan));
    }

    @Override
    @Transactional
    public PlanResponse updatePlan(Long id, PlanRequest r) {
        SubscriptionPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + id));
        if (r.getName() != null) plan.setName(r.getName());
        if (r.getDescription() != null) plan.setDescription(r.getDescription());
        if (r.getPrice() != null) plan.setPrice(r.getPrice());
        if (r.getCurrency() != null) plan.setCurrency(r.getCurrency());
        if (r.getBillingCycleDays() != null) plan.setBillingCycleDays(r.getBillingCycleDays());
        if (r.getMaxUsers() != null) plan.setMaxUsers(r.getMaxUsers());
        if (r.getTrialDays() != null) plan.setTrialDays(r.getTrialDays());
        if (r.getYearlyPrice() != null) plan.setYearlyPrice(r.getYearlyPrice());
        if (r.getYearlyDiscountPercent() != null) plan.setYearlyDiscountPercent(r.getYearlyDiscountPercent());
        if (r.getFeatures() != null) plan.setFeatures(r.getFeatures());
        if (r.getActive() != null) plan.setActive(r.getActive());
        return subscriptionMapper.toResponse(planRepository.save(plan));
    }

    // ---------- tenant subscription ----------

    @Override
    @Transactional
    public TenantResponse assignPlan(Long tenantId, String planCode, BillingCycle requestedCycle) {
        Tenant tenant = findTenant(tenantId);
        SubscriptionPlan plan = plan(planCode);
        BillingCycle cycle = requestedCycle != null ? requestedCycle : cycleOf(tenant);
        applyPlanNow(tenant, plan, cycle);
        record(tenant, SubscriptionAction.ASSIGNED,
                "Plan assigned directly to " + plan.getCode() + " (" + cycle + ")");
        notificationService.notifyTenant(tenant.getId(),
                com.priceintel.backend.constants.NotificationType.PLAN_ASSIGNED,
                "Your plan is now " + plan.getName(),
                "An administrator set your plan to " + plan.getName() + " (" + cycle + "). It's active now.",
                "/client/subscription");
        return tenantMapper.toResponse(tenant);
    }

    /**
     * Upgrade — client pays first. Creates a Stripe pay link and emails it to the
     * client; the plan is NOT changed until the payment webhook confirms. If Stripe
     * is not configured, the upgrade is applied immediately (mock).
     */
    @Override
    @Transactional
    public PlanChangeResponse upgrade(Long tenantId, String planCode, BillingCycle requestedCycle) {
        Tenant tenant = findTenant(tenantId);
        SubscriptionPlan plan = plan(planCode);
        BillingCycle cycle = requestedCycle != null ? requestedCycle : cycleOf(tenant);
        BigDecimal amount = priceFor(plan, cycle);

        if (!stripeService.isConfigured()) {
            applyPlanNow(tenant, plan, cycle);
            Payment paid = savePayment(tenant, plan, cycle, amount, PaymentType.UPGRADE,
                    PaymentStatus.PAID, "mock", null, "Upgrade applied (Stripe not configured)");
            paid.setPaidAt(Instant.now());
            paymentRepository.save(paid);
            record(tenant, SubscriptionAction.UPGRADED, "Upgraded to " + plan.getCode() + " (mock payment)");
            notificationService.notifyTenant(tenant.getId(),
                    com.priceintel.backend.constants.NotificationType.PLAN_UPGRADED,
                    "Upgraded to " + plan.getName(),
                    "Your plan has been upgraded to " + plan.getName() + " (" + cycle + ").",
                    "/client/subscription");
            return PlanChangeResponse.builder().tenant(tenantMapper.toResponse(tenant))
                    .outcome("APPLIED").payLinkSent(false)
                    .message("Stripe not configured — upgrade applied immediately.").build();
        }

        // Real flow: pending payment + Stripe checkout. The client is redirected
        // straight to checkoutUrl — no email is sent.
        Payment payment = savePayment(tenant, plan, cycle, amount, PaymentType.UPGRADE,
                PaymentStatus.PENDING, null, null, "Upgrade to " + plan.getCode() + " — awaiting payment");

        long cents = amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
        String success = frontendUrl + "/billing/success?session_id={CHECKOUT_SESSION_ID}";
        String cancel = frontendUrl + "/billing/cancel";
        StripeService.CheckoutSession session = stripeService.createCheckoutSession(
                cents, plan.getName() + " (" + cycle + ") — upgrade",
                PAY_REF_PREFIX + payment.getId(), success, cancel);
        payment.setStripeSessionId(session.id());
        paymentRepository.save(payment);

        record(tenant, SubscriptionAction.PAYMENT_REQUESTED,
                "Upgrade to " + plan.getCode() + " — awaiting payment");
        notificationService.notifyTenant(tenant.getId(),
                com.priceintel.backend.constants.NotificationType.PAYMENT_PENDING,
                "Complete your upgrade to " + plan.getName(),
                "Finish the payment to activate your " + plan.getName() + " (" + cycle + ") plan.",
                "/client/subscription");

        log.info("Upgrade checkout issued: tenant {} -> {} ({})", tenant.getId(), plan.getCode(), cycle);
        return PlanChangeResponse.builder().tenant(tenantMapper.toResponse(tenant))
                .outcome("UPGRADE_PENDING_PAYMENT").checkoutUrl(session.url()).payLinkSent(false)
                .message("Redirect the client to the checkout URL to complete the upgrade.").build();
    }

    /**
     * The customer pays for another period of the plan they already have.
     *
     * <p>Until now only the platform owner could renew, from the admin screen,
     * and that renewal took no payment. A customer whose plan ran out could only
     * get back in by upgrading to a dearer plan — and one already on the top
     * plan had no way back at all.</p>
     *
     * <p>Why not reuse upgrade with the same plan code: it starts the new period
     * from today, so a customer renewing five days early would lose five paid
     * days, and it records the payment as an upgrade.</p>
     */
    @Override
    @Transactional
    public PlanChangeResponse renewSelf(Long tenantId, BillingCycle requestedCycle) {
        Tenant tenant = findTenant(tenantId);
        if (tenant.getSubscriptionPlan() == null || tenant.getSubscriptionPlan().isBlank()) {
            throw new BadRequestException("There is no plan to renew yet — choose a plan to upgrade to.");
        }
        SubscriptionPlan plan = plan(tenant.getSubscriptionPlan());
        BillingCycle cycle = requestedCycle != null ? requestedCycle : cycleOf(tenant);
        BigDecimal amount = priceFor(plan, cycle);

        // A free plan cannot be renewed into another free period — that is what
        // made "free for 10 days" mean "free for ever": the trial ended, the
        // Renew button charged nothing, and the clock started again. Moving to
        // a paid plan is the way on.
        if (amount.signum() <= 0) {
            throw new BadRequestException("The " + plan.getName() + " plan cannot be renewed. "
                    + "Choose a paid plan to carry on — your data is kept either way.");
        }

        // Stripe not set up (dev): applied immediately so the flow can be tested.
        if (!stripeService.isConfigured()) {
            LocalDate newEnd = extendPeriod(tenant, plan, cycle);
            Payment paid = savePayment(tenant, plan, cycle, amount, PaymentType.RENEWAL,
                    PaymentStatus.PAID, "mock", null, "Renewal applied (Stripe not configured)");
            paid.setPaidAt(Instant.now());
            paymentRepository.save(paid);
            record(tenant, SubscriptionAction.RENEWED,
                    "Renewed " + plan.getCode() + " (" + cycle + ") until " + newEnd
                            + " (Stripe not configured)");
            notificationService.notifyTenant(tenant.getId(),
                    com.priceintel.backend.constants.NotificationType.PAYMENT_SUCCESS,
                    plan.getName() + " renewed",
                    "Your " + plan.getName() + " plan is renewed until " + newEnd + ".",
                    "/client/subscription");
            return PlanChangeResponse.builder().tenant(tenantMapper.toResponse(tenant))
                    .outcome("APPLIED").payLinkSent(false)
                    .message("Renewed until " + newEnd + ".").build();
        }

        Payment payment = savePayment(tenant, plan, cycle, amount, PaymentType.RENEWAL,
                PaymentStatus.PENDING, null, null, "Renewal of " + plan.getCode() + " — awaiting payment");

        long cents = amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
        String success = frontendUrl + "/billing/success?session_id={CHECKOUT_SESSION_ID}";
        String cancel = frontendUrl + "/billing/cancel";
        StripeService.CheckoutSession session = stripeService.createCheckoutSession(
                cents, plan.getName() + " (" + cycle + ") — renewal",
                PAY_REF_PREFIX + payment.getId(), success, cancel);
        payment.setStripeSessionId(session.id());
        paymentRepository.save(payment);

        record(tenant, SubscriptionAction.PAYMENT_REQUESTED,
                "Renewal of " + plan.getCode() + " (" + cycle + ") — awaiting payment");
        notificationService.notifyTenant(tenant.getId(),
                com.priceintel.backend.constants.NotificationType.PAYMENT_PENDING,
                "Complete your " + plan.getName() + " renewal",
                "Finish the payment to renew your " + plan.getName() + " (" + cycle + ") plan.",
                "/client/subscription");

        log.info("Renewal checkout issued: tenant {} -> {} ({})", tenant.getId(), plan.getCode(), cycle);
        return PlanChangeResponse.builder().tenant(tenantMapper.toResponse(tenant))
                .outcome("RENEWAL_PENDING_PAYMENT").checkoutUrl(session.url()).payLinkSent(false)
                .message("Redirect the client to the checkout URL to complete the renewal.").build();
    }

    /**
     * Adds one billing period. From the current end date if it has not passed —
     * renewing early loses nothing — otherwise from today, so an expired
     * customer is not charged for the weeks they were locked out.
     */
    private LocalDate extendPeriod(Tenant tenant, SubscriptionPlan plan, BillingCycle cycle) {
        LocalDate today = LocalDate.now();
        LocalDate end = tenant.getSubscriptionEndDate();
        LocalDate base = end != null && !end.isBefore(today) ? end : today;
        LocalDate newEnd = base.plusDays(cycle.getDays());
        tenant.setSubscriptionEndDate(newEnd);
        tenant.setBillingCycle(cycle);
        tenant.setMaxUsers(plan.getMaxUsers());
        tenant.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        // Renewing the current plan means keeping it: a downgrade scheduled for
        // the end of the old period no longer applies.
        clearScheduled(tenant);
        tenantRepository.save(tenant);
        return newEnd;
    }

    /**
     * Downgrade — no payment now. Scheduled for the end of the current period; a
     * daily job applies it and emails the client a pay link at that time.
     */
    @Override
    @Transactional
    public PlanChangeResponse downgrade(Long tenantId, String planCode, BillingCycle requestedCycle) {
        Tenant tenant = findTenant(tenantId);
        SubscriptionPlan plan = plan(planCode);
        LocalDate effective = tenant.getSubscriptionEndDate() != null
                ? tenant.getSubscriptionEndDate() : LocalDate.now();

        tenant.setScheduledPlanCode(plan.getCode());
        tenant.setScheduledBillingCycle(requestedCycle != null ? requestedCycle : cycleOf(tenant));
        tenant.setScheduledPlanEffectiveDate(effective);
        tenantRepository.save(tenant);

        record(tenant, SubscriptionAction.DOWNGRADE_SCHEDULED,
                "Downgrade to " + plan.getCode() + " scheduled for " + effective);
        notificationService.notifyTenant(tenant.getId(),
                com.priceintel.backend.constants.NotificationType.PLAN_DOWNGRADE_SCHEDULED,
                "Downgrade scheduled to " + plan.getName(),
                "Your plan will change to " + plan.getName() + " on " + effective
                        + ". Your current plan stays active until then.",
                "/client/subscription");
        log.info("Downgrade scheduled: tenant {} -> {} effective {}", tenant.getId(), plan.getCode(), effective);
        return PlanChangeResponse.builder().tenant(tenantMapper.toResponse(tenant))
                .outcome("DOWNGRADE_SCHEDULED").payLinkSent(false)
                .message("Downgrade to " + plan.getName() + " will take effect on " + effective
                        + ". The client will receive a pay link then.").build();
    }

    @Override
    @Transactional
    public TenantResponse renew(Long tenantId) {
        Tenant tenant = findTenant(tenantId);
        int cycleDays = planRepository.findByCodeIgnoreCase(
                        tenant.getSubscriptionPlan() == null ? "" : tenant.getSubscriptionPlan())
                .map(SubscriptionPlan::getBillingCycleDays).orElse(30);
        LocalDate base = tenant.getSubscriptionEndDate() != null
                && tenant.getSubscriptionEndDate().isAfter(LocalDate.now())
                ? tenant.getSubscriptionEndDate() : LocalDate.now();
        tenant.setSubscriptionEndDate(base.plusDays(cycleDays));
        tenant.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        tenantRepository.save(tenant);
        record(tenant, SubscriptionAction.RENEWED, "Renewed for " + cycleDays + " days");
        return tenantMapper.toResponse(tenant);
    }

    @Override
    @Transactional
    public TenantResponse extend(Long tenantId, int days) {
        Tenant tenant = findTenant(tenantId);
        LocalDate base = tenant.getSubscriptionEndDate() != null
                ? tenant.getSubscriptionEndDate() : LocalDate.now();
        tenant.setSubscriptionEndDate(base.plusDays(days));
        tenantRepository.save(tenant);
        record(tenant, SubscriptionAction.EXTENDED, "Extended by " + days + " days");
        return tenantMapper.toResponse(tenant);
    }

    @Override
    @Transactional
    public TenantResponse cancel(Long tenantId) {
        Tenant tenant = findTenant(tenantId);
        tenant.setSubscriptionStatus(SubscriptionStatus.CANCELLED);
        // A pending downgrade is moot once cancelled.
        clearScheduled(tenant);
        tenantRepository.save(tenant);
        record(tenant, SubscriptionAction.CANCELLED, "Subscription cancelled");
        return tenantMapper.toResponse(tenant);
    }

    @Override
    @Transactional
    public TenantResponse changeStatus(Long tenantId, SubscriptionStatus status) {
        Tenant tenant = findTenant(tenantId);
        tenant.setSubscriptionStatus(status);
        tenantRepository.save(tenant);
        record(tenant, SubscriptionAction.STATUS_CHANGED, "Status changed to " + status);
        return tenantMapper.toResponse(tenant);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<SubscriptionHistoryResponse> history(Long tenantId, int page, int size) {
        Pageable pageable = pageable(page, size);
        Page<SubscriptionHistoryResponse> result = historyRepository
                .findByTenantIdOrderByCreatedAtDesc(tenantId, pageable)
                .map(subscriptionMapper::toResponse);
        return PagedResponse.from(result);
    }

    // ---------- payments ----------

    @Override
    @Transactional
    public void applyPaidPayment(Long paymentId, String method) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BadRequestException("Unknown payment: " + paymentId));
        if (payment.getStatus() == PaymentStatus.PAID) {
            return; // idempotent — webhook can be delivered more than once
        }
        Tenant tenant = findTenant(payment.getTenantId());
        SubscriptionPlan plan = plan(payment.getPlanCode());
        BillingCycle cycle = payment.getBillingCycle() != null ? payment.getBillingCycle() : cycleOf(tenant);

        if (payment.getType() == PaymentType.RENEWAL) {
            applyPaidRenewal(payment, tenant, plan, cycle, method);
            return;
        }

        applyPlanNow(tenant, plan, cycle);

        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(Instant.now());
        payment.setMethod(method != null ? method : "card");
        paymentRepository.save(payment);

        SubscriptionAction action = payment.getType() == PaymentType.DOWNGRADE
                ? SubscriptionAction.DOWNGRADED : SubscriptionAction.UPGRADED;
        record(tenant, SubscriptionAction.PAYMENT_RECEIVED,
                "Payment received for " + plan.getCode() + " (" + cycle + ")");
        record(tenant, action, "Plan " + action.name().toLowerCase() + " to " + plan.getCode() + " after payment");

        mailService.sendWelcomeEmail(tenant, tenant.getCompanyEmail(), contactName(tenant),
                frontendUrl + "/login");

        com.priceintel.backend.constants.NotificationType nt = payment.getType() == PaymentType.DOWNGRADE
                ? com.priceintel.backend.constants.NotificationType.PLAN_DOWNGRADED
                : com.priceintel.backend.constants.NotificationType.PLAN_UPGRADED;
        notificationService.notifyTenant(tenant.getId(), nt,
                "Payment received — " + plan.getName() + " active",
                "Your payment succeeded and your " + plan.getName() + " (" + cycle + ") plan is now active.",
                "/client/subscription");
        notificationService.notifySuperAdmins(
                com.priceintel.backend.constants.NotificationType.PAYMENT_SUCCESS,
                "Payment received from " + tenant.getCompanyName(),
                tenant.getCompanyName() + " paid for the " + plan.getName() + " plan (" + cycle + ").",
                "/platform/clients/" + tenant.getId());

        log.info("Payment {} applied: tenant {} now on {} ({})",
                paymentId, tenant.getId(), plan.getCode(), cycle);
    }

    /**
     * A paid renewal extends the period rather than restarting it, and is not
     * announced as an upgrade — nor does it re-send the welcome email that
     * a first payment gets.
     */
    private void applyPaidRenewal(Payment payment, Tenant tenant, SubscriptionPlan plan,
                                  BillingCycle cycle, String method) {
        LocalDate newEnd = extendPeriod(tenant, plan, cycle);

        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(Instant.now());
        payment.setMethod(method != null ? method : "card");
        paymentRepository.save(payment);

        record(tenant, SubscriptionAction.PAYMENT_RECEIVED,
                "Renewal payment received for " + plan.getCode() + " (" + cycle + ")");
        record(tenant, SubscriptionAction.RENEWED,
                "Renewed " + plan.getCode() + " (" + cycle + ") until " + newEnd + " after payment");

        notificationService.notifyTenant(tenant.getId(),
                com.priceintel.backend.constants.NotificationType.PAYMENT_SUCCESS,
                "Payment received — " + plan.getName() + " renewed",
                "Your payment succeeded and your " + plan.getName() + " plan is renewed until " + newEnd + ".",
                "/client/subscription");
        notificationService.notifySuperAdmins(
                com.priceintel.backend.constants.NotificationType.PAYMENT_SUCCESS,
                "Renewal received from " + tenant.getCompanyName(),
                tenant.getCompanyName() + " renewed the " + plan.getName() + " plan (" + cycle
                        + ") until " + newEnd + ".",
                "/platform/clients/" + tenant.getId());

        log.info("Renewal payment {} applied: tenant {} on {} until {}",
                payment.getId(), tenant.getId(), plan.getCode(), newEnd);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<PaymentResponse> listPayments(int page, int size) {
        return PagedResponse.from(paymentRepository
                .findAllByOrderByCreatedAtDesc(pageable(page, size)).map(this::toPaymentResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<PaymentResponse> listTenantPayments(Long tenantId, int page, int size) {
        return PagedResponse.from(paymentRepository
                .findByTenantIdOrderByCreatedAtDesc(tenantId, pageable(page, size)).map(this::toPaymentResponse));
    }

    /**
     * Applies scheduled downgrades whose effective date has arrived. The new
     * (lower) plan starts now and the client is emailed a Stripe pay link for it
     * (a pending payment row is created). Runs from the daily scheduler.
     */
    @Override
    @Transactional
    public int runDueScheduledDowngrades() {
        LocalDate today = LocalDate.now();
        List<Tenant> due = tenantRepository
                .findByScheduledPlanCodeIsNotNullAndScheduledPlanEffectiveDateLessThanEqual(today);
        int applied = 0;
        for (Tenant tenant : due) {
            try {
                SubscriptionPlan plan = plan(tenant.getScheduledPlanCode());
                BillingCycle cycle = tenant.getScheduledBillingCycle() != null
                        ? tenant.getScheduledBillingCycle() : cycleOf(tenant);
                BigDecimal amount = priceFor(plan, cycle);

                // New plan starts now (grace) — client keeps access; pay link follows.
                applyPlanNow(tenant, plan, cycle);
                clearScheduled(tenant);
                tenantRepository.save(tenant);
                record(tenant, SubscriptionAction.DOWNGRADED,
                        "Scheduled downgrade to " + plan.getCode() + " applied");
                notificationService.notifyTenant(tenant.getId(),
                        com.priceintel.backend.constants.NotificationType.PLAN_DOWNGRADED,
                        "Your plan changed to " + plan.getName(),
                        "Your scheduled downgrade to " + plan.getName() + " (" + cycle + ") is now in effect.",
                        "/client/subscription");

                Payment payment = savePayment(tenant, plan, cycle, amount, PaymentType.DOWNGRADE,
                        stripeService.isConfigured() ? PaymentStatus.PENDING : PaymentStatus.PAID,
                        stripeService.isConfigured() ? null : "mock", null,
                        "Scheduled downgrade to " + plan.getCode());

                if (stripeService.isConfigured()) {
                    long cents = amount.multiply(BigDecimal.valueOf(100))
                            .setScale(0, RoundingMode.HALF_UP).longValueExact();
                    StripeService.CheckoutSession session = stripeService.createCheckoutSession(
                            cents, plan.getName() + " (" + cycle + ") — plan change",
                            PAY_REF_PREFIX + payment.getId(),
                            frontendUrl + "/billing/success?session_id={CHECKOUT_SESSION_ID}",
                            frontendUrl + "/billing/cancel");
                    payment.setStripeSessionId(session.id());
                    paymentRepository.save(payment);
                    mailService.sendPlanChangePayLinkEmail(tenant.getCompanyEmail(), contactName(tenant),
                            plan.getName(), cycle.name(), amount, currencyOf(tenant), session.url(), false);
                    record(tenant, SubscriptionAction.PAYMENT_REQUESTED,
                            "Downgrade pay link sent to client");
                } else {
                    payment.setPaidAt(Instant.now());
                    paymentRepository.save(payment);
                }
                applied++;
            } catch (Exception e) {
                log.error("Failed to apply scheduled downgrade for tenant {}: {}",
                        tenant.getId(), e.getMessage());
            }
        }
        if (applied > 0) {
            log.info("Applied {} scheduled downgrade(s)", applied);
        }
        return applied;
    }

    /**
     * Reconciliation (scheduled): for every pending upgrade/downgrade payment,
     * ask Stripe whether it was paid and apply it if so. Safety net for when the
     * webhook never fires and the success page never calls /confirm.
     */
    @Override
    @Transactional
    public int reconcilePendingPayments() {
        if (!stripeService.isConfigured()) {
            return 0;
        }
        int completed = 0;
        for (Payment payment : paymentRepository
                .findByStatusAndStripeSessionIdIsNotNull(PaymentStatus.PENDING)) {
            try {
                if (stripeService.retrieveSession(payment.getStripeSessionId()).isPaid()) {
                    applyPaidPayment(payment.getId(), "card");
                    completed++;
                }
            } catch (Exception e) {
                log.warn("Reconcile payment {} failed: {}", payment.getId(), e.getMessage());
            }
        }
        if (completed > 0) {
            log.info("Reconciled {} plan-change payment(s)", completed);
        }
        return completed;
    }

    /**
     * Nightly: make the stored status agree with the dates.
     *
     * <p>Nothing did this before. A trial that ended stayed TRIAL for ever, and
     * a company whose paid period ran out stayed ACTIVE — so the login check
     * for EXPIRED, which already existed, could never fire. The admin screen
     * showed "TRIAL · Expired 52d ago" because it worked the date out itself.</p>
     *
     * <p>Access does not wait for this: the request check works from the dates
     * directly. This keeps the status, the admin screen and the history honest.
     * Suspended companies are left alone — that status is the admin's call.</p>
     */
    @Override
    @Transactional
    public int runExpirySweep() {
        LocalDate today = LocalDate.now();
        int grace = accessService.getGraceDays();
        int changed = 0;
        for (Tenant tenant : tenantRepository.findByStatusAndSubscriptionStatusIn(TenantStatus.ACTIVE,
                java.util.List.of(SubscriptionStatus.TRIAL, SubscriptionStatus.ACTIVE))) {
            LocalDate paid = SubscriptionAccessService.paidThrough(tenant);
            if (SubscriptionAccessService.isLapsed(tenant, today, grace)) {
                tenant.setSubscriptionStatus(SubscriptionStatus.EXPIRED);
                tenantRepository.save(tenant);
                record(tenant, SubscriptionAction.EXPIRED,
                        "Paid through " + paid + "; expired after " + grace + " grace day(s)");
                // Tell them, or the first they hear of it is a refused search.
                try {
                    reminderService.sendExpiredNotice(tenant);
                } catch (RuntimeException e) {
                    log.warn("Expired notice for tenant {} failed: {}", tenant.getId(), e.getMessage());
                }
                changed++;
            } else if (tenant.getSubscriptionStatus() == SubscriptionStatus.TRIAL
                    && tenant.getTrialEndDate() != null && tenant.getTrialEndDate().isBefore(today)
                    && tenant.getSubscriptionEndDate() != null
                    && tenant.getSubscriptionEndDate().isAfter(tenant.getTrialEndDate())) {
                // The trial is over but the period after it is covered — Patuli
                // paid for a year beyond its trial and was still labelled TRIAL.
                tenant.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
                tenantRepository.save(tenant);
                record(tenant, SubscriptionAction.TRIAL_ENDED,
                        "Trial ended " + tenant.getTrialEndDate() + "; covered until "
                                + tenant.getSubscriptionEndDate());
                changed++;
            }
        }
        if (changed > 0) {
            log.info("Expiry sweep: {} compan{} updated", changed, changed == 1 ? "y" : "ies");
        }
        return changed;
    }

    // ---------- helpers ----------

    private void applyPlanNow(Tenant tenant, SubscriptionPlan plan, BillingCycle cycle) {
        LocalDate today = LocalDate.now();
        tenant.setSubscriptionPlan(plan.getCode());
        tenant.setBillingCycle(cycle);
        tenant.setMaxUsers(plan.getMaxUsers());
        tenant.setSubscriptionStartDate(today);
        tenant.setSubscriptionEndDate(today.plusDays(cycle.getDays()));
        tenant.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            tenant.setStatus(TenantStatus.ACTIVE);
        }
        // An applied/paid change supersedes any pending scheduled one.
        clearScheduled(tenant);
        tenantRepository.save(tenant);
    }

    private void clearScheduled(Tenant tenant) {
        tenant.setScheduledPlanCode(null);
        tenant.setScheduledBillingCycle(null);
        tenant.setScheduledPlanEffectiveDate(null);
    }

    private Payment savePayment(Tenant tenant, SubscriptionPlan plan, BillingCycle cycle, BigDecimal amount,
                                PaymentType type, PaymentStatus status, String method,
                                String stripeSessionId, String note) {
        return paymentRepository.save(Payment.builder()
                .tenantId(tenant.getId()).planCode(plan.getCode()).billingCycle(cycle)
                .amount(amount).currency(currencyOf(tenant)).type(type).status(status)
                .method(method).stripeSessionId(stripeSessionId).note(note).build());
    }

    private BigDecimal priceFor(SubscriptionPlan plan, BillingCycle cycle) {
        return cycle == BillingCycle.YEARLY ? plan.computeYearlyPrice() : plan.getPrice();
    }

    private BillingCycle cycleOf(Tenant tenant) {
        return tenant.getBillingCycle() != null ? tenant.getBillingCycle() : BillingCycle.MONTHLY;
    }

    private String currencyOf(Tenant tenant) {
        return tenant.getCurrency() != null ? tenant.getCurrency() : "USD";
    }

    private String contactName(Tenant tenant) {
        return tenant.getContactPerson() != null && !tenant.getContactPerson().isBlank()
                ? tenant.getContactPerson() : tenant.getCompanyName();
    }

    private Pageable pageable(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException("Invalid pagination parameters");
        }
        return PageRequest.of(page, size);
    }

    private Tenant findTenant(Long id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + id));
    }

    private SubscriptionPlan plan(String planCode) {
        return planRepository.findByCodeIgnoreCase(planCode == null ? "" : planCode)
                .orElseThrow(() -> new BadRequestException("Unknown plan: " + planCode));
    }

    private void record(Tenant tenant, SubscriptionAction action, String note) {
        historyRepository.save(SubscriptionHistory.builder()
                .tenantId(tenant.getId()).action(action)
                .planCode(tenant.getSubscriptionPlan()).status(tenant.getSubscriptionStatus())
                .startDate(tenant.getSubscriptionStartDate()).endDate(tenant.getSubscriptionEndDate())
                .note(note).build());
        // Every subscription change passes through here, so this is where the
        // cached access decision is dropped — a renewal takes effect on the
        // customer's next click, not thirty seconds later.
        accessService.evict(tenant.getId());
        log.info("Tenant {} subscription: {} ({})", tenant.getId(), action, note);
    }

    private PaymentResponse toPaymentResponse(Payment p) {
        return PaymentResponse.builder()
                .id(p.getId()).tenantId(p.getTenantId()).planCode(p.getPlanCode())
                .billingCycle(p.getBillingCycle() != null ? p.getBillingCycle().name() : null)
                .amount(p.getAmount()).currency(p.getCurrency())
                .type(p.getType() != null ? p.getType().name() : null)
                .status(p.getStatus() != null ? p.getStatus().name() : null)
                .method(p.getMethod()).stripeSessionId(p.getStripeSessionId())
                .paidAt(p.getPaidAt()).createdAt(p.getCreatedAt()).note(p.getNote())
                .build();
    }
}
