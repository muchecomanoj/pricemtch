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
import com.priceintel.backend.constants.SubscriptionAction;
import com.priceintel.backend.constants.SubscriptionStatus;
import com.priceintel.backend.constants.TenantStatus;
import com.priceintel.backend.constants.UserStatus;
import com.priceintel.backend.dto.request.CheckoutRequest;
import com.priceintel.backend.dto.request.ChoosePlanRequest;
import com.priceintel.backend.dto.request.CompleteProfileRequest;
import com.priceintel.backend.dto.request.SetPasswordRequest;
import com.priceintel.backend.dto.request.ValidateActivationRequest;
import com.priceintel.backend.dto.request.VerifyActivationCodeRequest;
import com.priceintel.backend.dto.response.ActivationCompleteResponse;
import com.priceintel.backend.dto.response.ActivationValidationResponse;
import com.priceintel.backend.dto.response.CheckoutResponse;
import com.priceintel.backend.entity.ActivationToken;
import com.priceintel.backend.entity.SubscriptionHistory;
import com.priceintel.backend.entity.SubscriptionPlan;
import com.priceintel.backend.entity.Tenant;
import com.priceintel.backend.entity.User;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.exception.ResourceNotFoundException;
import com.priceintel.backend.payment.StripeService;
import com.priceintel.backend.repository.ActivationTokenRepository;
import com.priceintel.backend.repository.PaymentRepository;
import com.priceintel.backend.repository.SubscriptionHistoryRepository;
import com.priceintel.backend.repository.SubscriptionPlanRepository;
import com.priceintel.backend.repository.TenantRepository;
import com.priceintel.backend.repository.UserRepository;
import com.priceintel.backend.service.MailService;
import com.priceintel.backend.service.OnboardingService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OnboardingServiceImpl implements OnboardingService {

    private final ActivationTokenRepository tokenRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionHistoryRepository historyRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final StripeService stripeService;
    private final PaymentRepository paymentRepository;
    private final String frontendUrl;
    private final int expiryHours;

    private static final int MAX_ATTEMPTS = 5;

    public OnboardingServiceImpl(ActivationTokenRepository tokenRepository, TenantRepository tenantRepository,
                                 UserRepository userRepository, SubscriptionPlanRepository planRepository,
                                 SubscriptionHistoryRepository historyRepository, PasswordEncoder passwordEncoder,
                                 MailService mailService, StripeService stripeService,
                                 PaymentRepository paymentRepository,
                                 @Value("${app.frontend-url}") String frontendUrl,
                                 @Value("${app.activation.expiry-hours:48}") int expiryHours) {
        this.tokenRepository = tokenRepository;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.planRepository = planRepository;
        this.historyRepository = historyRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.stripeService = stripeService;
        this.paymentRepository = paymentRepository;
        this.frontendUrl = frontendUrl;
        this.expiryHours = expiryHours;
    }

    @Override
    @Transactional
    public void issueActivation(Tenant tenant, User adminUser) {
        tokenRepository.deleteByUserId(adminUser.getId());
        String rawToken = randomToken();
        String code = randomCode();
        tokenRepository.save(ActivationToken.builder()
                .tokenHash(sha256(rawToken)).codeHash(passwordEncoder.encode(code))
                .tenantId(tenant.getId()).userId(adminUser.getId())
                .expiresAt(Instant.now().plusSeconds(expiryHours * 3600L))
                .used(false).attempts(0).build());
        String activationUrl = frontendUrl + "/activate?token=" + rawToken;
        mailService.sendActivationEmail(tenant, adminUser.getEmail(), adminUser.getFullName(),
                activationUrl, code, expiryHours);
        log.info("Activation issued for tenant {} / user {}", tenant.getCompanyCode(), adminUser.getEmail());
        // DEV aid: also log the link + code so the flow is testable without opening the inbox.
        // Remove or lower this before production.
        log.debug("[DEV] Activation for {} -> url={} code={}", adminUser.getEmail(), activationUrl, code);
    }

    @Override
    @Transactional(readOnly = true)
    public ActivationValidationResponse validate(ValidateActivationRequest request) {
        ActivationToken token = loadUsableToken(request.getToken());
        Tenant tenant = tenant(token.getTenantId());
        User user = user(token.getUserId());
        return ActivationValidationResponse.builder()
                .valid(true).email(user.getEmail())
                .companyName(tenant.getCompanyName()).companyCode(tenant.getCompanyCode())
                .contactPerson(tenant.getContactPerson()).expiresAt(token.getExpiresAt())
                .assignedPlan(tenant.getSubscriptionPlan())
                .assignedBillingCycle(tenant.getBillingCycle() != null ? tenant.getBillingCycle().name() : null)
                .build();
    }

    @Override
    @Transactional
    public ActivationValidationResponse verifyCode(VerifyActivationCodeRequest request) {
        ActivationToken token = loadUsableToken(request.getToken());
        assertCode(token, request.getCode());
        return validate(new ValidateActivationRequest(request.getToken()));
    }

    @Override
    @Transactional
    public ActivationCompleteResponse setPassword(SetPasswordRequest request) {
        ActivationToken token = loadUsableToken(request.getToken());
        assertCode(token, request.getCode());

        Tenant tenant = tenant(token.getTenantId());
        User user = user(token.getUserId());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        // Verified, but not live until a plan is chosen and paid for.
        tenant.setStatus(TenantStatus.PENDING_PAYMENT);
        tenantRepository.save(tenant);

        log.info("Password set for {} (tenant {} -> PENDING_PAYMENT)", user.getEmail(), tenant.getCompanyCode());
        return step(tenant, user.getEmail(), false, "COMPLETE_PROFILE");
    }

    @Override
    @Transactional
    public ActivationCompleteResponse completeProfile(CompleteProfileRequest r) {
        ActivationToken token = loadUsableToken(r.getToken());
        Tenant tenant = tenant(token.getTenantId());
        if (r.getCompanyName() != null && !r.getCompanyName().isBlank()) tenant.setCompanyName(r.getCompanyName());
        if (r.getCompanyAddress() != null) tenant.setCompanyAddress(r.getCompanyAddress());
        if (r.getCompanyPhone() != null) tenant.setCompanyPhone(r.getCompanyPhone());
        if (r.getWebsite() != null) tenant.setWebsite(r.getWebsite());
        if (r.getCity() != null) tenant.setCity(r.getCity());
        if (r.getState() != null) tenant.setState(r.getState());
        if (r.getCountry() != null) tenant.setCountry(r.getCountry());
        if (r.getPostalCode() != null) tenant.setPostalCode(r.getPostalCode());
        if (r.getTimezone() != null) tenant.setTimezone(r.getTimezone());
        if (r.getCurrency() != null) tenant.setCurrency(r.getCurrency());
        if (r.getGstin() != null) tenant.setGstin(r.getGstin());
        if (r.getTaxId() != null) tenant.setTaxId(r.getTaxId());
        tenantRepository.save(tenant);
        return step(tenant, user(token.getUserId()).getEmail(), false, "CHOOSE_PLAN");
    }

    @Override
    @Transactional
    public ActivationCompleteResponse choosePlan(ChoosePlanRequest r) {
        ActivationToken token = loadUsableToken(r.getToken());
        Tenant tenant = tenant(token.getTenantId());
        SubscriptionPlan plan = planRepository.findByCodeIgnoreCase(r.getPlanCode())
                .orElseThrow(() -> new BadRequestException("Unknown plan: " + r.getPlanCode()));
        tenant.setSubscriptionPlan(plan.getCode());
        tenant.setBillingCycle(r.getBillingCycle());
        tenant.setMaxUsers(plan.getMaxUsers());
        tenantRepository.save(tenant);
        return step(tenant, user(token.getUserId()).getEmail(), false, "PAYMENT");
    }

    @Override
    @Transactional
    public CheckoutResponse startCheckout(CheckoutRequest r) {
        ActivationToken token = loadUsableToken(r.getToken());
        Tenant tenant = tenant(token.getTenantId());
        SubscriptionPlan plan = requireChosenPlan(tenant);
        BillingCycle cycle = tenant.getBillingCycle();
        BigDecimal amount = priceFor(plan, cycle);
        long cents = amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();

        String description = plan.getName() + " (" + cycle + ")";

        // Same as self sign-up: nothing to charge on a free plan, and Stripe
        // rejects a zero-amount checkout, so activate the account here instead.
        if (amount.signum() <= 0) {
            finalizeActivation(token);
            return CheckoutResponse.builder().mode("FREE")
                    .planCode(plan.getCode()).billingCycle(cycle.name())
                    .amount(BigDecimal.ZERO)
                    .currency(tenant.getCurrency() != null ? tenant.getCurrency() : "USD")
                    .trialDays(plan.getTrialDays())
                    .note("No payment needed on the " + plan.getName()
                            + " plan — the account is ready. Sign in to start.")
                    .build();
        }

        if (stripeService.isConfigured()) {
            String success = r.getSuccessUrl() != null ? r.getSuccessUrl()
                    : frontendUrl + "/onboarding/success?token=" + r.getToken()
                      + "&session_id={CHECKOUT_SESSION_ID}";
            String cancel = r.getCancelUrl() != null ? r.getCancelUrl()
                    : frontendUrl + "/onboarding/cancel";
            StripeService.CheckoutSession session =
                    stripeService.createCheckoutSession(cents, description, r.getToken(), success, cancel);
            // Remember the session so a background job can reconcile the payment
            // even if the webhook never fires and the success page never confirms.
            token.setStripeSessionId(session.id());
            tokenRepository.save(token);
            return CheckoutResponse.builder()
                    .mode("STRIPE").checkoutUrl(session.url()).sessionId(session.id())
                    .planCode(plan.getCode()).billingCycle(cycle.name())
                    .amount(amount).currency(tenant.getCurrency() != null ? tenant.getCurrency() : "USD")
                    .trialDays(plan.getTrialDays())
                    .note("Redirect the user to checkoutUrl to pay.").build();
        }
        // No gateway configured — mock mode.
        return CheckoutResponse.builder()
                .mode("MOCK").planCode(plan.getCode()).billingCycle(cycle.name())
                .amount(amount).currency(tenant.getCurrency() != null ? tenant.getCurrency() : "USD")
                .trialDays(plan.getTrialDays())
                .note("Stripe is not configured. Call POST /api/public/payment/mock-confirm "
                        + "with this token to simulate a successful payment.").build();
    }

    @Override
    @Transactional
    public ActivationCompleteResponse completePayment(String rawToken) {
        ActivationToken token = tokenRepository.findByTokenHash(sha256(rawToken))
                .orElseThrow(() -> new BadRequestException("Unknown payment reference"));
        return finalizeActivation(token);
    }

    /**
     * Reconciliation (scheduled): for every onboarding checkout still awaiting
     * payment, ask Stripe whether it was paid and activate if so. Removes any
     * dependency on the webhook firing or the success page calling /confirm.
     */
    @Override
    @Transactional
    public int reconcilePendingCheckouts() {
        if (!stripeService.isConfigured()) {
            return 0;
        }
        int completed = 0;
        for (ActivationToken token : tokenRepository.findByUsedFalseAndStripeSessionIdIsNotNull()) {
            Tenant tenant = tenantRepository.findById(token.getTenantId()).orElse(null);
            if (tenant == null || tenant.getStatus() == TenantStatus.ACTIVE) {
                continue;
            }
            try {
                if (stripeService.retrieveSession(token.getStripeSessionId()).isPaid()) {
                    finalizeActivation(token);
                    completed++;
                }
            } catch (Exception e) {
                log.warn("Reconcile onboarding token {} failed: {}", token.getId(), e.getMessage());
            }
        }
        if (completed > 0) {
            log.info("Reconciled {} onboarding payment(s)", completed);
        }
        return completed;
    }

    /** Activates the tenant for a paid onboarding token (idempotent). */
    private ActivationCompleteResponse finalizeActivation(ActivationToken token) {
        Tenant tenant = tenant(token.getTenantId());
        User user = user(token.getUserId());

        // Idempotent: if already activated, just return the final state.
        if (token.isUsed() || tenant.getStatus() == TenantStatus.ACTIVE) {
            return finalResponse(tenant, user.getEmail());
        }

        SubscriptionPlan plan = requireChosenPlan(tenant);
        BillingCycle cycle = tenant.getBillingCycle();
        int trialDays = plan.getTrialDays();
        LocalDate today = LocalDate.now();
        LocalDate trialEnd = today.plusDays(trialDays);
        // Same rule as self sign-up: a free plan's trial is its whole period.
        LocalDate periodEnd = com.priceintel.backend.utils.PlanPeriod.accessEnds(
                priceFor(plan, cycle), trialEnd, cycle);

        tenant.setSubscriptionStartDate(today);
        tenant.setTrialEndDate(trialDays > 0 ? trialEnd : null);
        tenant.setSubscriptionEndDate(periodEnd);
        tenant.setSubscriptionStatus(trialDays > 0 ? SubscriptionStatus.TRIAL : SubscriptionStatus.ACTIVE);
        tenant.setStatus(TenantStatus.ACTIVE);
        tenantRepository.save(tenant);

        historyRepository.save(SubscriptionHistory.builder()
                .tenantId(tenant.getId()).action(SubscriptionAction.ASSIGNED)
                .planCode(plan.getCode()).status(tenant.getSubscriptionStatus())
                .startDate(today).endDate(periodEnd)
                .note("Subscription activated via payment (" + cycle + ", trial " + trialDays + "d)").build());

        token.setUsed(true);
        tokenRepository.save(token);

        // Record the initial payment for the super admin's payment history.
        paymentRepository.save(com.priceintel.backend.entity.Payment.builder()
                .tenantId(tenant.getId()).planCode(plan.getCode()).billingCycle(cycle)
                .amount(priceFor(plan, cycle)).currency(tenant.getCurrency() != null ? tenant.getCurrency() : "USD")
                .type(com.priceintel.backend.constants.PaymentType.INITIAL)
                .status(com.priceintel.backend.constants.PaymentStatus.PAID)
                .method("card").paidAt(Instant.now())
                .note("Onboarding initial payment").build());

        String loginUrl = frontendUrl + "/login";
        mailService.sendWelcomeEmail(tenant, user.getEmail(), user.getFullName(), loginUrl);

        log.info("Payment complete; tenant {} ACTIVE ({} / {}, trial {}d)",
                tenant.getCompanyCode(), plan.getCode(), cycle, trialDays);
        return finalResponse(tenant, user.getEmail());
    }

    @Override
    @Transactional
    public void resendActivation(Long tenantId) {
        Tenant tenant = tenant(tenantId);
        if (tenant.getStatus() != TenantStatus.PENDING_ACTIVATION
                && tenant.getStatus() != TenantStatus.PENDING_PAYMENT) {
            throw new BadRequestException("This client is already activated");
        }
        User admin = userRepository
                .findAll(com.priceintel.backend.repository.UserSpecifications
                        .withFilters(tenantId, null, AccessType.ADMIN, null))
                .stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("No admin user for this client"));
        issueActivation(tenant, admin);
    }

    // ---------- helpers ----------

    private SubscriptionPlan requireChosenPlan(Tenant tenant) {
        if (tenant.getSubscriptionPlan() == null || tenant.getBillingCycle() == null) {
            throw new BadRequestException("Select a plan and billing cycle before payment");
        }
        return planRepository.findByCodeIgnoreCase(tenant.getSubscriptionPlan())
                .orElseThrow(() -> new BadRequestException("Selected plan no longer exists"));
    }

    private BigDecimal priceFor(SubscriptionPlan plan, BillingCycle cycle) {
        return cycle == BillingCycle.YEARLY ? plan.computeYearlyPrice() : plan.getPrice();
    }

    private ActivationCompleteResponse step(Tenant tenant, String email, boolean activated, String nextStep) {
        return ActivationCompleteResponse.builder()
                .activated(activated).nextStep(nextStep).email(email)
                .companyName(tenant.getCompanyName()).companyCode(tenant.getCompanyCode())
                .subscriptionPlan(tenant.getSubscriptionPlan())
                .billingCycle(tenant.getBillingCycle() != null ? tenant.getBillingCycle().name() : null)
                .build();
    }

    private ActivationCompleteResponse finalResponse(Tenant tenant, String email) {
        return ActivationCompleteResponse.builder()
                .activated(true).nextStep("DONE").email(email)
                .companyName(tenant.getCompanyName()).companyCode(tenant.getCompanyCode())
                .subscriptionPlan(tenant.getSubscriptionPlan())
                .billingCycle(tenant.getBillingCycle() != null ? tenant.getBillingCycle().name() : null)
                .subscriptionStatus(String.valueOf(tenant.getSubscriptionStatus()))
                .subscriptionStartDate(tenant.getSubscriptionStartDate())
                .trialEndDate(tenant.getTrialEndDate())
                .subscriptionEndDate(tenant.getSubscriptionEndDate())
                .loginUrl(frontendUrl + "/login").build();
    }

    private ActivationToken loadUsableToken(String rawToken) {
        ActivationToken token = tokenRepository.findByTokenHash(sha256(rawToken))
                .orElseThrow(() -> new BadRequestException("This activation link is not valid."));
        if (token.isUsed()) {
            throw new BadRequestException("This activation link has already been used.");
        }
        if (token.isExpired()) {
            throw new BadRequestException("This activation link has expired. Please request a new one.");
        }
        if (token.getAttempts() >= MAX_ATTEMPTS) {
            throw new BadRequestException("Too many incorrect attempts. Please request a new activation link.");
        }
        return token;
    }

    private void assertCode(ActivationToken token, String code) {
        if (!passwordEncoder.matches(code, token.getCodeHash())) {
            token.setAttempts(token.getAttempts() + 1);
            tokenRepository.save(token);
            int left = Math.max(0, MAX_ATTEMPTS - token.getAttempts());
            throw new BadRequestException("Incorrect verification code. " + left + " attempt(s) remaining.");
        }
    }

    private Tenant tenant(Long id) {
        return tenantRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Client not found"));
    }

    private User user(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
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
