package com.priceintel.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.priceintel.backend.constants.BillingCycle;
import com.priceintel.backend.constants.SubscriptionStatus;
import com.priceintel.backend.constants.TenantStatus;
import com.priceintel.backend.entity.SubscriptionPlan;
import com.priceintel.backend.entity.Tenant;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.repository.PaymentRepository;
import com.priceintel.backend.repository.SubscriptionHistoryRepository;
import com.priceintel.backend.repository.SubscriptionPlanRepository;
import com.priceintel.backend.repository.TenantRepository;
import com.priceintel.backend.service.impl.SubscriptionAccessService;
import com.priceintel.backend.service.impl.SubscriptionReminderService;
import com.priceintel.backend.service.impl.SubscriptionServiceImpl;
import com.priceintel.backend.utils.PlanPeriod;

/**
 * The trial model: a free plan is the trial, and paid plans are paid.
 *
 * <p>Both rules exist because "Free for 10 days" did not hold. The period was
 * the trial plus a billing cycle, making it 40 days, and the Renew button cost
 * nothing, so it could be restarted for ever.</p>
 */
class FreeTrialModelTest {

    @Nested
    @DisplayName("how long the free period runs")
    class Period {

        private final LocalDate today = LocalDate.of(2026, 9, 29);

        @Test
        @DisplayName("a free plan's trial is the whole period — 10 days means 10 days")
        void freePeriodIsTheTrial() {
            LocalDate trialEnd = today.plusDays(10);

            assertThat(PlanPeriod.accessEnds(BigDecimal.ZERO, trialEnd, BillingCycle.MONTHLY))
                    .isEqualTo(today.plusDays(10));
            // Yearly billing must not turn a 10-day trial into a year either.
            assertThat(PlanPeriod.accessEnds(BigDecimal.ZERO, trialEnd, BillingCycle.YEARLY))
                    .isEqualTo(today.plusDays(10));
        }

        @Test
        @DisplayName("a paid plan gets the period it paid for, after any trial")
        void paidPeriodAddsTheCycle() {
            LocalDate noTrial = today;      // paid plans now have no trial
            assertThat(PlanPeriod.accessEnds(new BigDecimal("149.00"), noTrial, BillingCycle.MONTHLY))
                    .isEqualTo(today.plusDays(30));
            assertThat(PlanPeriod.accessEnds(new BigDecimal("1430.40"), noTrial, BillingCycle.YEARLY))
                    .isEqualTo(today.plusDays(365));
        }
    }

    @Nested
    @DisplayName("renewing")
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT)
    class Renewal {

        @Mock TenantRepository tenantRepository;
        @Mock SubscriptionPlanRepository planRepository;
        @Mock SubscriptionHistoryRepository historyRepository;
        @Mock PaymentRepository paymentRepository;
        @Mock com.priceintel.backend.mapper.SubscriptionMapper subscriptionMapper;
        @Mock com.priceintel.backend.mapper.TenantMapper tenantMapper;
        @Mock com.priceintel.backend.payment.StripeService stripeService;
        @Mock MailService mailService;
        @Mock NotificationService notificationService;
        @Mock SubscriptionAccessService accessService;
        @Mock SubscriptionReminderService reminderService;

        @InjectMocks SubscriptionServiceImpl service;

        private Tenant onPlan(String code, String monthlyPrice) {
            SubscriptionPlan plan = new SubscriptionPlan();
            plan.setCode(code);
            plan.setName(code.equals("FREE") ? "Free" : "Professional");
            plan.setPrice(new BigDecimal(monthlyPrice));
            plan.setMaxUsers(3);
            when(planRepository.findByCodeIgnoreCase(code)).thenReturn(Optional.of(plan));

            Tenant t = new Tenant();
            t.setId(5L);
            t.setStatus(TenantStatus.ACTIVE);
            t.setSubscriptionStatus(SubscriptionStatus.TRIAL);
            t.setSubscriptionPlan(code);
            t.setBillingCycle(BillingCycle.MONTHLY);
            t.setSubscriptionEndDate(LocalDate.now().minusDays(1));
            when(tenantRepository.findById(5L)).thenReturn(Optional.of(t));
            return t;
        }

        @Test
        @DisplayName("a free plan cannot be renewed into another free period")
        void freeCannotRenew() {
            Tenant free = onPlan("FREE", "0.00");

            assertThatThrownBy(() -> service.renewSelf(5L, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("cannot be renewed")
                    .hasMessageContaining("paid plan")
                    // Nobody should fear losing their work by not renewing.
                    .hasMessageContaining("data is kept");

            assertThat(free.getSubscriptionEndDate()).isEqualTo(LocalDate.now().minusDays(1));
            verify(stripeService, never()).createCheckoutSession(anyLong(), anyString(), anyString(),
                    anyString(), anyString());
        }

        @Test
        @DisplayName("a paid plan renews as before, through the payment page")
        void paidStillRenews() {
            onPlan("PRO", "149.00");
            when(stripeService.isConfigured()).thenReturn(true);
            when(stripeService.createCheckoutSession(anyLong(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(new com.priceintel.backend.payment.StripeService.CheckoutSession(
                            "cs_2", "https://checkout.stripe.com/c/pay/cs_2"));
            when(paymentRepository.save(any())).thenAnswer(i -> {
                var p = (com.priceintel.backend.entity.Payment) i.getArgument(0);
                p.setId(1L);
                return p;
            });

            var response = service.renewSelf(5L, null);

            assertThat(response.getOutcome()).isEqualTo("RENEWAL_PENDING_PAYMENT");
            assertThat(response.getCheckoutUrl()).startsWith("https://checkout.stripe.com/");
        }
    }
}
