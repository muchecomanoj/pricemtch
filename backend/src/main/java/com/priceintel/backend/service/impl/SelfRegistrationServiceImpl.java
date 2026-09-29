package com.priceintel.backend.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.priceintel.backend.constants.AccessType;
import com.priceintel.backend.constants.BillingCycle;
import com.priceintel.backend.constants.SelfRegistrationStatus;
import com.priceintel.backend.constants.SubscriptionAction;
import com.priceintel.backend.constants.SubscriptionStatus;
import com.priceintel.backend.constants.TenantStatus;
import com.priceintel.backend.constants.UserStatus;
import com.priceintel.backend.dto.request.RegisterSetPasswordRequest;
import com.priceintel.backend.dto.request.RegisterVerifyRequest;
import com.priceintel.backend.dto.request.SelfRegisterRequest;
import com.priceintel.backend.dto.response.CheckoutResponse;
import com.priceintel.backend.dto.response.SelfRegisterResponse;
import com.priceintel.backend.entity.SelfRegistration;
import com.priceintel.backend.entity.SubscriptionHistory;
import com.priceintel.backend.entity.SubscriptionPlan;
import com.priceintel.backend.entity.Tenant;
import com.priceintel.backend.entity.User;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.exception.DuplicateResourceException;
import com.priceintel.backend.payment.StripeService;
import com.priceintel.backend.repository.SelfRegistrationRepository;
import com.priceintel.backend.repository.SubscriptionHistoryRepository;
import com.priceintel.backend.repository.SubscriptionPlanRepository;
import com.priceintel.backend.repository.PaymentRepository;
import com.priceintel.backend.repository.TenantRepository;
import com.priceintel.backend.repository.UserRepository;
import com.priceintel.backend.service.MailService;
import com.priceintel.backend.service.SelfRegistrationService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SelfRegistrationServiceImpl implements SelfRegistrationService {

    private final SelfRegistrationRepository registrationRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionHistoryRepository historyRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final StripeService stripeService;
    private final PaymentRepository paymentRepository;
    private final String frontendUrl;
    private final int expiryMinutes;

    private static final int MAX_ATTEMPTS = 5;
    /** Stripe client_reference prefix so the webhook routes to self-registration. */
    public static final String REF_PREFIX = "reg:";

    public SelfRegistrationServiceImpl(SelfRegistrationRepository registrationRepository,
                                       TenantRepository tenantRepository, UserRepository userRepository,
                                       SubscriptionPlanRepository planRepository,
                                       SubscriptionHistoryRepository historyRepository,
                                       PasswordEncoder passwordEncoder, MailService mailService,
                                       StripeService stripeService, PaymentRepository paymentRepository,
                                       @Value("${app.frontend-url}") String frontendUrl,
                                       @Value("${app.self-registration.expiry-minutes:30}") int expiryMinutes) {
        this.registrationRepository = registrationRepository;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.planRepository = planRepository;
        this.historyRepository = historyRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.stripeService = stripeService;
        this.paymentRepository = paymentRepository;
        this.frontendUrl = frontendUrl;
        this.expiryMinutes = expiryMinutes;
    }

    @Override
    @Transactional
    public SelfRegisterResponse register(SelfRegisterRequest r) {
        // Email must not already belong to a real account.
        if (userRepository.existsByEmail(r.getEmail())) {
            throw new DuplicateResourceException("An account with this email already exists. Please log in.");
        }
        // Plan must be valid.
        SubscriptionPlan plan = planRepository.findByCodeIgnoreCase(r.getPlanCode())
                .orElseThrow(() -> new BadRequestException("Unknown plan: " + r.getPlanCode()
                        + ". See GET /api/public/subscription-plans."));

        // One live signup per email — clear any abandoned one.
        registrationRepository.deleteIncompleteByEmail(r.getEmail());

        String rawToken = randomToken();
        String code = randomCode();
        SelfRegistration reg = SelfRegistration.builder()
                .tokenHash(sha256(rawToken)).codeHash(passwordEncoder.encode(code))
                .email(r.getEmail())
                .companyName(r.getCompanyName()).companyPhone(r.getCompanyPhone())
                .companyAddress(r.getCompanyAddress()).city(r.getCity()).state(r.getState())
                .country(r.getCountry()).postalCode(r.getPostalCode()).timezone(r.getTimezone())
                .currency(r.getCurrency()).website(r.getWebsite()).contactPerson(r.getContactPerson())
                .industry(r.getIndustry()).gstin(r.getGstin()).taxId(r.getTaxId())
                .planCode(plan.getCode()).billingCycle(r.getBillingCycle())
                .status(SelfRegistrationStatus.PENDING_VERIFICATION)
                .expiresAt(Instant.now().plusSeconds(expiryMinutes * 60L))
                .attempts(0).build();
        registrationRepository.save(reg);

        mailService.sendVerificationCodeEmail(r.getEmail(), displayName(r), r.getCompanyName(), code, expiryMinutes);
        log.info("Self-registration started for {} (plan {})", r.getEmail(), plan.getCode());
        log.debug("[DEV] Self-reg for {} -> token={} code={}", r.getEmail(), rawToken, code);

        return SelfRegisterResponse.builder()
                .token(rawToken).email(r.getEmail())
                .status(reg.getStatus().name()).nextStep("VERIFY_CODE").completed(false).build();
    }

    @Override
    @Transactional
    public SelfRegisterResponse verifyCode(RegisterVerifyRequest r) {
        SelfRegistration reg = loadUsable(r.getToken());
        assertCode(reg, r.getCode());
        reg.setStatus(SelfRegistrationStatus.VERIFIED);
        registrationRepository.save(reg);
        return step(reg, r.getToken(), "SET_PASSWORD");
    }

    @Override
    @Transactional
    public SelfRegisterResponse setPassword(RegisterSetPasswordRequest r) {
        SelfRegistration reg = loadUsable(r.getToken());
        assertCode(reg, r.getCode());
        reg.setPasswordHash(passwordEncoder.encode(r.getPassword()));
        reg.setStatus(SelfRegistrationStatus.PENDING_PAYMENT);
        registrationRepository.save(reg);
        return step(reg, r.getToken(), "PAYMENT");
    }

    @Override
    @Transactional
    public CheckoutResponse checkout(String token) {
        SelfRegistration reg = loadUsable(token);
        if (reg.getPasswordHash() == null) {
            throw new BadRequestException("Set a password before payment");
        }
        SubscriptionPlan plan = plan(reg.getPlanCode());
        BillingCycle cycle = reg.getBillingCycle();
        BigDecimal amount = priceFor(plan, cycle);
        long cents = amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
        String currency = reg.getCurrency() != null ? reg.getCurrency() : "USD";

        if (stripeService.isConfigured()) {
            String success = frontendUrl + "/register/success?token=" + token
                    + "&session_id={CHECKOUT_SESSION_ID}";
            String cancel = frontendUrl + "/register/cancel";
            StripeService.CheckoutSession session = stripeService.createCheckoutSession(
                    cents, plan.getName() + " (" + cycle + ")", REF_PREFIX + token, success, cancel);
            // Remember the session so a background job can reconcile the payment.
            reg.setStripeSessionId(session.id());
            registrationRepository.save(reg);
            return CheckoutResponse.builder().mode("STRIPE").checkoutUrl(session.url()).sessionId(session.id())
                    .planCode(plan.getCode()).billingCycle(cycle.name()).amount(amount).currency(currency)
                    .trialDays(plan.getTrialDays()).note("Redirect the user to checkoutUrl to pay.").build();
        }
        return CheckoutResponse.builder().mode("MOCK")
                .planCode(plan.getCode()).billingCycle(cycle.name()).amount(amount).currency(currency)
                .trialDays(plan.getTrialDays())
                .note("Stripe not configured. POST /api/public/register/mock-confirm with this token to simulate.")
                .build();
    }

    @Override
    @Transactional
    public SelfRegisterResponse completePayment(String token) {
        SelfRegistration reg = registrationRepository.findByTokenHash(sha256(token))
                .orElseThrow(() -> new BadRequestException("Unknown registration reference"));
        return finalizeRegistration(reg, token);
    }

    /**
     * Reconciliation (scheduled): for every self-registration awaiting payment,
     * ask Stripe whether it was paid and materialize the tenant if so. Removes
     * any dependency on the webhook or the success page calling /confirm.
     */
    @Override
    @Transactional
    public int reconcilePendingCheckouts() {
        if (!stripeService.isConfigured()) {
            return 0;
        }
        int completed = 0;
        for (SelfRegistration reg : registrationRepository
                .findByStatusAndStripeSessionIdIsNotNull(SelfRegistrationStatus.PENDING_PAYMENT)) {
            try {
                if (stripeService.retrieveSession(reg.getStripeSessionId()).isPaid()) {
                    finalizeRegistration(reg, null);
                    completed++;
                }
            } catch (Exception e) {
                log.warn("Reconcile self-registration {} failed: {}", reg.getId(), e.getMessage());
            }
        }
        if (completed > 0) {
            log.info("Reconciled {} self-registration payment(s)", completed);
        }
        return completed;
    }

    /** Materializes the tenant for a paid self-registration (idempotent). */
    private SelfRegisterResponse finalizeRegistration(SelfRegistration reg, String token) {
        // Idempotent: if already completed, return the created tenant's state.
        if (reg.getStatus() == SelfRegistrationStatus.COMPLETED && reg.getCreatedTenantId() != null) {
            Tenant existing = tenantRepository.findById(reg.getCreatedTenantId()).orElse(null);
            return existing != null ? completedResponse(reg, existing) : step(reg, token, "DONE");
        }
        if (reg.getStatus() != SelfRegistrationStatus.PENDING_PAYMENT || reg.getPasswordHash() == null) {
            throw new BadRequestException("Complete verification and set a password before payment");
        }
        if (userRepository.existsByEmail(reg.getEmail())) {
            throw new DuplicateResourceException("An account with this email already exists.");
        }

        SubscriptionPlan plan = plan(reg.getPlanCode());
        BillingCycle cycle = reg.getBillingCycle();
        int trialDays = plan.getTrialDays();
        LocalDate today = LocalDate.now();
        LocalDate trialEnd = today.plusDays(trialDays);
        LocalDate periodEnd = trialEnd.plusDays(cycle.getDays());

        // NOW create the real tenant.
        Tenant tenant = Tenant.builder()
                .companyName(resolveCompanyName(reg.getCompanyName())).companyCode(resolveCompanyCode(reg.getCompanyName()))
                .companyEmail(reg.getEmail()).companyPhone(reg.getCompanyPhone())
                .companyAddress(reg.getCompanyAddress()).city(reg.getCity()).state(reg.getState())
                .country(reg.getCountry()).postalCode(reg.getPostalCode()).timezone(reg.getTimezone())
                .currency(reg.getCurrency()).website(reg.getWebsite()).contactPerson(reg.getContactPerson())
                .industry(reg.getIndustry()).gstin(reg.getGstin()).taxId(reg.getTaxId())
                .subscriptionPlan(plan.getCode()).billingCycle(cycle).maxUsers(plan.getMaxUsers())
                .subscriptionStartDate(today).trialEndDate(trialDays > 0 ? trialEnd : null)
                .subscriptionEndDate(periodEnd)
                .subscriptionStatus(trialDays > 0 ? SubscriptionStatus.TRIAL : SubscriptionStatus.ACTIVE)
                .status(TenantStatus.ACTIVE).build();
        tenant = tenantRepository.save(tenant);

        // The admin user (password already hashed during set-password).
        String[] name = splitName(reg.getContactPerson());
        User admin = User.builder()
                .tenantId(tenant.getId()).superAdmin(false)
                .firstName(name[0]).lastName(name[1])
                .email(reg.getEmail()).password(reg.getPasswordHash())
                .accessType(AccessType.ADMIN).status(UserStatus.ACTIVE).build();
        userRepository.save(admin);

        historyRepository.save(SubscriptionHistory.builder()
                .tenantId(tenant.getId()).action(SubscriptionAction.ASSIGNED)
                .planCode(plan.getCode()).status(tenant.getSubscriptionStatus())
                .startDate(today).endDate(periodEnd)
                .note("Self-registration paid (" + cycle + ", trial " + trialDays + "d)").build());

        reg.setStatus(SelfRegistrationStatus.COMPLETED);
        reg.setCreatedTenantId(tenant.getId());
        registrationRepository.save(reg);

        // Record the initial payment for the super admin's payment history.
        paymentRepository.save(com.priceintel.backend.entity.Payment.builder()
                .tenantId(tenant.getId()).planCode(plan.getCode()).billingCycle(cycle)
                .amount(priceFor(plan, cycle)).currency(tenant.getCurrency() != null ? tenant.getCurrency() : "USD")
                .type(com.priceintel.backend.constants.PaymentType.INITIAL)
                .status(com.priceintel.backend.constants.PaymentStatus.PAID)
                .method("card").paidAt(Instant.now())
                .note("Self-registration initial payment").build());

        mailService.sendWelcomeEmail(tenant, admin.getEmail(), admin.getFullName(), frontendUrl + "/login");
        log.info("Self-registration completed: tenant {} created for {}", tenant.getCompanyCode(), reg.getEmail());
        return completedResponse(reg, tenant);
    }

    // ---------- helpers ----------

    private SelfRegistration loadUsable(String rawToken) {
        SelfRegistration reg = registrationRepository.findByTokenHash(sha256(rawToken))
                .orElseThrow(() -> new BadRequestException("This registration session is not valid."));
        if (reg.getStatus() == SelfRegistrationStatus.COMPLETED) {
            throw new BadRequestException("This registration is already complete. Please log in.");
        }
        if (reg.isExpired()) {
            throw new BadRequestException("This registration session has expired. Please start again.");
        }
        if (reg.getAttempts() >= MAX_ATTEMPTS) {
            throw new BadRequestException("Too many incorrect attempts. Please start again.");
        }
        return reg;
    }

    private void assertCode(SelfRegistration reg, String code) {
        if (!passwordEncoder.matches(code, reg.getCodeHash())) {
            reg.setAttempts(reg.getAttempts() + 1);
            registrationRepository.save(reg);
            int left = Math.max(0, MAX_ATTEMPTS - reg.getAttempts());
            throw new BadRequestException("Incorrect verification code. " + left + " attempt(s) remaining.");
        }
    }

    private SubscriptionPlan plan(String code) {
        return planRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new BadRequestException("Selected plan no longer exists: " + code));
    }

    private BigDecimal priceFor(SubscriptionPlan plan, BillingCycle cycle) {
        return cycle == BillingCycle.YEARLY ? plan.computeYearlyPrice() : plan.getPrice();
    }

    private String resolveCompanyCode(String companyName) {
        String base = companyName.toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (base.isEmpty()) {
            base = "TENANT";
        }
        base = base.substring(0, Math.min(base.length(), 12));
        String candidate = base;
        int suffix = 1;
        while (tenantRepository.existsByCompanyCodeIgnoreCase(candidate)) {
            candidate = base + (++suffix);
        }
        return candidate;
    }

    /**
     * The tenants table has a unique constraint on company_name. Two different
     * clients can legitimately pick the same name, so append " (2)", " (3)", …
     * instead of letting the insert fail (which used to 500 the payment webhook
     * and leave the registration stuck at PENDING_PAYMENT).
     */
    private String resolveCompanyName(String companyName) {
        String base = (companyName == null || companyName.isBlank()) ? "Company" : companyName.trim();
        String candidate = base;
        int suffix = 1;
        while (tenantRepository.existsByCompanyNameIgnoreCase(candidate)) {
            candidate = base + " (" + (++suffix) + ")";
        }
        return candidate;
    }

    private String[] splitName(String contactPerson) {
        if (contactPerson != null && !contactPerson.isBlank()) {
            String[] parts = contactPerson.trim().split("\\s+", 2);
            return new String[]{parts[0], parts.length > 1 ? parts[1] : "Admin"};
        }
        return new String[]{"Account", "Admin"};
    }

    private String displayName(SelfRegisterRequest r) {
        return (r.getContactPerson() != null && !r.getContactPerson().isBlank())
                ? r.getContactPerson() : r.getCompanyName();
    }

    private SelfRegisterResponse step(SelfRegistration reg, String token, String nextStep) {
        return SelfRegisterResponse.builder()
                .token(token).email(reg.getEmail()).status(reg.getStatus().name())
                .nextStep(nextStep).completed(false).build();
    }

    private SelfRegisterResponse completedResponse(SelfRegistration reg, Tenant tenant) {
        return SelfRegisterResponse.builder()
                .email(reg.getEmail()).status("COMPLETED").nextStep("DONE").completed(true)
                .companyCode(tenant.getCompanyCode()).subscriptionPlan(tenant.getSubscriptionPlan())
                .billingCycle(tenant.getBillingCycle() != null ? tenant.getBillingCycle().name() : null)
                .subscriptionStatus(String.valueOf(tenant.getSubscriptionStatus()))
                .trialEndDate(tenant.getTrialEndDate()).subscriptionEndDate(tenant.getSubscriptionEndDate())
                .loginUrl(frontendUrl + "/login").build();
    }

    private String randomToken() {
        byte[] buf = new byte[32];
        new SecureRandom().nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private String randomCode() {
        return String.format("%06d", new SecureRandom().nextInt(1_000_000));
    }

    private String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not hash token", e);
        }
    }
}
