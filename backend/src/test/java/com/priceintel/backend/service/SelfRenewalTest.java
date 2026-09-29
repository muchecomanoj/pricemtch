package com.priceintel.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.priceintel.backend.constants.AccountAccess;
import com.priceintel.backend.constants.BillingCycle;
import com.priceintel.backend.constants.PaymentStatus;
import com.priceintel.backend.constants.PaymentType;
import com.priceintel.backend.constants.SubscriptionAction;
import com.priceintel.backend.constants.SubscriptionStatus;
import com.priceintel.backend.constants.TenantStatus;
import com.priceintel.backend.entity.Payment;
import com.priceintel.backend.entity.SubscriptionHistory;
import com.priceintel.backend.entity.SubscriptionPlan;
import com.priceintel.backend.entity.Tenant;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.payment.StripeService;
import com.priceintel.backend.repository.PaymentRepository;
import com.priceintel.backend.repository.SubscriptionHistoryRepository;
import com.priceintel.backend.repository.SubscriptionPlanRepository;
import com.priceintel.backend.repository.TenantRepository;
import com.priceintel.backend.security.AccountAccessPolicy;
import com.priceintel.backend.service.impl.SubscriptionAccessService;
import com.priceintel.backend.service.impl.SubscriptionServiceImpl;

/**
 * A customer renewing their own plan — before, only the platform owner could,
 * and without taking a payment.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SelfRenewalTest {

    @Mock TenantRepository tenantRepository;
    @Mock SubscriptionPlanRepository planRepository;
    @Mock SubscriptionHistoryRepository historyRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock com.priceintel.backend.mapper.SubscriptionMapper subscriptionMapper;
    @Mock com.priceintel.backend.mapper.TenantMapper tenantMapper;
    @Mock StripeService stripeService;
    @Mock MailService mailService;
    @Mock NotificationService notificationService;
    @Mock SubscriptionAccessService accessService;
    @Mock com.priceintel.backend.service.impl.SubscriptionReminderService reminderService;

    @InjectMocks SubscriptionServiceImpl service;

    private final LocalDate today = LocalDate.now();
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        SubscriptionPlan pro = new SubscriptionPlan();
        pro.setCode("PRO");
        pro.setName("Pro");
        pro.setPrice(new BigDecimal("49.00"));
        pro.setMaxUsers(10);
        when(planRepository.findByCodeIgnoreCase("PRO")).thenReturn(Optional.of(pro));

        tenant = new Tenant();
        tenant.setId(62L);
        tenant.setCompanyName("Price Matrix (2)");
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        tenant.setSubscriptionPlan("PRO");
        tenant.setBillingCycle(BillingCycle.MONTHLY);
        when(tenantRepository.findById(62L)).thenReturn(Optional.of(tenant));

        AtomicLong ids = new AtomicLong(500);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(ids.incrementAndGet());
            }
            return p;
        });
    }

    @Test
    @DisplayName("renewing early adds the month to the end date — the paid days are not lost")
    void earlyRenewalKeepsRemainingDays() {
        when(stripeService.isConfigured()).thenReturn(false);
        tenant.setSubscriptionEndDate(today.plusDays(5));

        service.renewSelf(62L, null);

        // Upgrade would have set today + 30 and thrown away the five paid days.
        assertThat(tenant.getSubscriptionEndDate()).isEqualTo(today.plusDays(35));
        assertThat(tenant.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("an expired customer renews from today, not from the old end date")
    void expiredRenewalStartsToday() {
        when(stripeService.isConfigured()).thenReturn(false);
        tenant.setSubscriptionStatus(SubscriptionStatus.EXPIRED);
        tenant.setSubscriptionEndDate(today.minusDays(20));

        service.renewSelf(62L, null);

        // Not charged for the three weeks they were locked out.
        assertThat(tenant.getSubscriptionEndDate()).isEqualTo(today.plusDays(30));
        assertThat(tenant.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(SubscriptionAccessService.accessFor(tenant, today, 3)).isEqualTo(AccountAccess.FULL);
    }

    @Test
    @DisplayName("with Stripe, nothing changes until the customer pays")
    void stripeCheckoutIsPending() {
        when(stripeService.isConfigured()).thenReturn(true);
        when(stripeService.createCheckoutSession(anyLong(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new StripeService.CheckoutSession("cs_test_1", "https://checkout.stripe.com/c/pay/cs_test_1"));
        tenant.setSubscriptionStatus(SubscriptionStatus.EXPIRED);
        tenant.setSubscriptionEndDate(today.minusDays(20));

        var res = service.renewSelf(62L, null);

        assertThat(res.getOutcome()).isEqualTo("RENEWAL_PENDING_PAYMENT");
        assertThat(res.getCheckoutUrl()).startsWith("https://checkout.stripe.com/");
        assertThat(tenant.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(tenant.getSubscriptionEndDate()).isEqualTo(today.minusDays(20));

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        Payment payment = saved.getValue();
        assertThat(payment.getType()).isEqualTo(PaymentType.RENEWAL);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getAmount()).isEqualByComparingTo("49.00");
        verify(stripeService).createCheckoutSession(eq(4900L), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("when the payment lands, the period is extended and recorded as a renewal")
    void paidRenewalExtends() {
        tenant.setSubscriptionEndDate(today.plusDays(4));
        Payment pending = Payment.builder().tenantId(62L).planCode("PRO").billingCycle(BillingCycle.MONTHLY)
                .amount(new BigDecimal("49.00")).type(PaymentType.RENEWAL).status(PaymentStatus.PENDING).build();
        pending.setId(900L);
        when(paymentRepository.findById(900L)).thenReturn(Optional.of(pending));

        service.applyPaidPayment(900L, "card");

        assertThat(tenant.getSubscriptionEndDate()).isEqualTo(today.plusDays(34));
        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.PAID);
        ArgumentCaptor<SubscriptionHistory> history = ArgumentCaptor.forClass(SubscriptionHistory.class);
        verify(historyRepository, org.mockito.Mockito.times(2)).save(history.capture());
        assertThat(history.getAllValues()).extracting(SubscriptionHistory::getAction)
                .containsExactly(SubscriptionAction.PAYMENT_RECEIVED, SubscriptionAction.RENEWED);
        // Not an upgrade, so not a second welcome email.
        verify(mailService, never()).sendWelcomeEmail(any(), any(), any(), any());
    }

    @Test
    @DisplayName("renewing cancels a downgrade that was scheduled for the end of the old period")
    void renewalClearsScheduledDowngrade() {
        when(stripeService.isConfigured()).thenReturn(false);
        tenant.setSubscriptionEndDate(today.plusDays(2));
        tenant.setScheduledPlanCode("BASIC");
        tenant.setScheduledPlanEffectiveDate(today.plusDays(2));

        service.renewSelf(62L, null);

        assertThat(tenant.getScheduledPlanCode()).isNull();
        assertThat(tenant.getScheduledPlanEffectiveDate()).isNull();
    }

    @Test
    @DisplayName("a company with no plan is told to choose one")
    void noPlan() {
        tenant.setSubscriptionPlan(null);
        assertThatThrownBy(() -> service.renewSelf(62L, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("choose a plan");
    }

    @Test
    @DisplayName("an expired account may call renew — it is the way back in")
    void expiredMayRenew() {
        assertThat(AccountAccessPolicy.allows(AccountAccess.READ_ONLY, "POST",
                "/api/v1/client/subscription/renew")).isTrue();
        // A suspended one may not: that is the admin's decision to reverse.
        assertThat(AccountAccessPolicy.allows(AccountAccess.BLOCKED, "POST",
                "/api/v1/client/subscription/renew")).isFalse();
    }
}
