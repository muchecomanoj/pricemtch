package com.priceintel.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.priceintel.backend.constants.BillingCycle;
import com.priceintel.backend.constants.SubscriptionStatus;
import com.priceintel.backend.constants.TenantStatus;
import com.priceintel.backend.dto.response.CheckoutResponse;
import com.priceintel.backend.entity.SelfRegistration;
import com.priceintel.backend.entity.SubscriptionPlan;
import com.priceintel.backend.entity.Tenant;
import com.priceintel.backend.entity.User;
import com.priceintel.backend.payment.StripeService;
import com.priceintel.backend.repository.PaymentRepository;
import com.priceintel.backend.repository.SelfRegistrationRepository;
import com.priceintel.backend.repository.SubscriptionHistoryRepository;
import com.priceintel.backend.repository.SubscriptionPlanRepository;
import com.priceintel.backend.repository.TenantRepository;
import com.priceintel.backend.repository.UserRepository;
import com.priceintel.backend.service.impl.SelfRegistrationServiceImpl;

/**
 * Signing up on the Free plan.
 *
 * <p>Seen live on 29 Sep: choosing Free still sent the user to the payment
 * step, and Stripe answered "The Checkout Session's total amount due cannot be
 * zero in payment mode" — a 400 at the last step of sign-up, with no account
 * created. Nothing is charged for a free plan, so nothing should be sent to
 * the payment provider.</p>
 */
class FreePlanSignupTest {

    private final SelfRegistrationRepository registrations = mock(SelfRegistrationRepository.class);
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final SubscriptionPlanRepository plans = mock(SubscriptionPlanRepository.class);
    private final SubscriptionHistoryRepository history = mock(SubscriptionHistoryRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final MailService mail = mock(MailService.class);
    private final StripeService stripe = mock(StripeService.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);

    private final SelfRegistrationServiceImpl service = new SelfRegistrationServiceImpl(
            registrations, tenants, users, plans, history, passwords, mail, stripe, payments,
            "http://app.example", 30);

    private static final String TOKEN = "reg-token-abc";

    @BeforeEach
    void setUp() {
        // Stripe is configured and working — the point is that it is not asked.
        when(stripe.isConfigured()).thenReturn(true);
        when(tenants.save(any(Tenant.class))).thenAnswer(i -> {
            Tenant t = i.getArgument(0);
            t.setId(900L);
            return t;
        });
        when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(tenants.existsByCompanyCodeIgnoreCase(anyString())).thenReturn(false);
        when(tenants.existsByCompanyNameIgnoreCase(anyString())).thenReturn(false);
    }

    private void registrationOn(String planCode, String monthlyPrice, BillingCycle cycle) {
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setCode(planCode);
        plan.setName(planCode.equals("FREE") ? "Free" : "Professional");
        plan.setPrice(new BigDecimal(monthlyPrice));
        plan.setMaxUsers(3);
        plan.setTrialDays(planCode.equals("FREE") ? 0 : 14);
        when(plans.findByCodeIgnoreCase(planCode)).thenReturn(Optional.of(plan));

        SelfRegistration reg = new SelfRegistration();
        reg.setEmail("jayaprakash@suyogindia.com");
        reg.setCompanyName("Raibow Products");
        reg.setContactPerson("Jaya");
        reg.setCurrency("USD");
        reg.setPlanCode(planCode);
        reg.setBillingCycle(cycle);
        reg.setPasswordHash("already-set");
        // The state a user is in at the payment step: verified, password set.
        reg.setStatus(com.priceintel.backend.constants.SelfRegistrationStatus.PENDING_PAYMENT);
        reg.setExpiresAt(Instant.now().plusSeconds(600));
        when(registrations.findByTokenHash(anyString())).thenReturn(Optional.of(reg));
        when(registrations.save(any(SelfRegistration.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("Free yearly: no payment page, and the workspace is created")
    void freePlanSkipsPayment() {
        registrationOn("FREE", "0.00", BillingCycle.YEARLY);   // the sign-up page defaults to yearly

        CheckoutResponse response = service.checkout(TOKEN);

        assertThat(response.getMode()).isEqualTo("FREE");
        assertThat(response.getCheckoutUrl()).isNull();
        assertThat(response.getAmount()).isEqualByComparingTo("0.00");
        assertThat(response.getNote()).contains("No payment needed").contains("Sign in");
        // Stripe is never asked for a zero-amount session — that is the 400.
        verify(stripe, never()).createCheckoutSession(anyLong(), anyString(), anyString(),
                anyString(), anyString());
        // The account really exists afterwards, active rather than awaiting payment.
        var saved = org.mockito.ArgumentCaptor.forClass(Tenant.class);
        verify(tenants).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(saved.getValue().getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(users).save(any(User.class));
    }

    @Test
    @DisplayName("Free monthly behaves the same way")
    void freeMonthly() {
        registrationOn("FREE", "0.00", BillingCycle.MONTHLY);

        assertThat(service.checkout(TOKEN).getMode()).isEqualTo("FREE");
        verify(stripe, never()).createCheckoutSession(anyLong(), anyString(), anyString(),
                anyString(), anyString());
    }

    @Test
    @DisplayName("a paid plan still goes to the payment page")
    void paidPlanStillCharges() {
        registrationOn("PRO", "149.00", BillingCycle.MONTHLY);
        when(stripe.createCheckoutSession(anyLong(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new StripeService.CheckoutSession("cs_1", "https://checkout.stripe.com/c/pay/cs_1"));

        CheckoutResponse response = service.checkout(TOKEN);

        assertThat(response.getMode()).isEqualTo("STRIPE");
        assertThat(response.getCheckoutUrl()).startsWith("https://checkout.stripe.com/");
        // 149.00 → 14900 cents.
        verify(stripe).createCheckoutSession(org.mockito.ArgumentMatchers.eq(14900L), anyString(),
                anyString(), anyString(), anyString());
        // Nothing is created until the payment succeeds.
        verify(tenants, never()).save(any(Tenant.class));
    }
}
