package com.priceintel.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.priceintel.backend.constants.SubscriptionAction;
import com.priceintel.backend.constants.SubscriptionStatus;
import com.priceintel.backend.constants.TenantStatus;
import com.priceintel.backend.entity.SubscriptionHistory;
import com.priceintel.backend.entity.Tenant;
import com.priceintel.backend.repository.PaymentRepository;
import com.priceintel.backend.repository.SubscriptionHistoryRepository;
import com.priceintel.backend.repository.SubscriptionPlanRepository;
import com.priceintel.backend.repository.TenantRepository;
import com.priceintel.backend.service.impl.SubscriptionAccessService;
import com.priceintel.backend.service.impl.SubscriptionServiceImpl;

/** The nightly job that makes stored status agree with the dates. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionExpirySweepTest {

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
    @Mock com.priceintel.backend.service.impl.SubscriptionReminderService reminderService;

    @InjectMocks SubscriptionServiceImpl service;

    private static Tenant tenant(long id, SubscriptionStatus sub, LocalDate trialEnd, LocalDate paidThrough) {
        Tenant t = new Tenant();
        t.setId(id);
        t.setStatus(TenantStatus.ACTIVE);
        t.setSubscriptionStatus(sub);
        t.setTrialEndDate(trialEnd);
        t.setSubscriptionEndDate(paidThrough);
        return t;
    }

    @Test
    @DisplayName("lapsed companies expire, paid-past trials become active, the rest are untouched")
    void sweep() {
        LocalDate today = LocalDate.now();
        when(accessService.getGraceDays()).thenReturn(3);

        Tenant lapsed = tenant(1, SubscriptionStatus.ACTIVE, null, today.minusDays(5));
        Tenant unpaidTrial = tenant(2, SubscriptionStatus.TRIAL, today.minusDays(52), today.minusDays(22));
        Tenant paidPastTrial = tenant(3, SubscriptionStatus.TRIAL, today.minusDays(29), today.plusDays(336));
        Tenant inGrace = tenant(4, SubscriptionStatus.ACTIVE, null, today.minusDays(2));
        Tenant runningTrial = tenant(5, SubscriptionStatus.TRIAL, today.plusDays(5), today.plusDays(35));
        when(tenantRepository.findByStatusAndSubscriptionStatusIn(any(), any()))
                .thenReturn(List.of(lapsed, unpaidTrial, paidPastTrial, inGrace, runningTrial));

        int changed = service.runExpirySweep();

        assertThat(changed).isEqualTo(3);
        assertThat(lapsed.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(unpaidTrial.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(paidPastTrial.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(inGrace.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);   // two days late: grace
        assertThat(runningTrial.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.TRIAL);

        // Each change is written to the history, which the admin screen shows.
        ArgumentCaptor<SubscriptionHistory> history = ArgumentCaptor.forClass(SubscriptionHistory.class);
        verify(historyRepository, org.mockito.Mockito.times(3)).save(history.capture());
        assertThat(history.getAllValues()).extracting(SubscriptionHistory::getAction)
                .containsExactlyInAnyOrder(SubscriptionAction.EXPIRED, SubscriptionAction.EXPIRED,
                        SubscriptionAction.TRIAL_ENDED);
        // And the cached decision is dropped, so access changes on the next request.
        verify(accessService).evict(1L);
        verify(accessService).evict(3L);
        verify(accessService, never()).evict(4L);
        // The two that expired are told so; the trial that became active is not.
        verify(reminderService).sendExpiredNotice(lapsed);
        verify(reminderService).sendExpiredNotice(unpaidTrial);
        verify(reminderService, never()).sendExpiredNotice(paidPastTrial);
    }
}
