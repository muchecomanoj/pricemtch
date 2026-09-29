package com.priceintel.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.priceintel.backend.constants.AccessType;
import com.priceintel.backend.constants.BillingCycle;
import com.priceintel.backend.constants.EmailTemplateKey;
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
import com.priceintel.backend.service.impl.SubscriptionAccessService;
import com.priceintel.backend.service.impl.SubscriptionReminderService;

/** Reminder emails before a plan ends, and the notice when it has. */
class SubscriptionReminderTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 22);

    private final TenantRepository tenants = mock(TenantRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final SubscriptionPlanRepository plans = mock(SubscriptionPlanRepository.class);
    private final SubscriptionReminderRepository sent = mock(SubscriptionReminderRepository.class);
    private final MailService mail = mock(MailService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final SubscriptionAccessService access = mock(SubscriptionAccessService.class);

    private final SubscriptionReminderService service = new SubscriptionReminderService(
            tenants, users, plans, sent, mail, notifications, access, "7,1", "http://app.example");

    @BeforeEach
    void setUp() {
        when(access.getGraceDays()).thenReturn(3);
        SubscriptionPlan pro = new SubscriptionPlan();
        pro.setCode("PRO");
        pro.setName("Pro");
        pro.setPrice(new BigDecimal("49.00"));
        when(plans.findByCodeIgnoreCase("PRO")).thenReturn(Optional.of(pro));
        when(users.findByTenantId(any())).thenReturn(List.of());
    }

    private static Tenant tenant(long id, LocalDate paidThrough) {
        Tenant t = new Tenant();
        t.setId(id);
        t.setCompanyName("Company " + id);
        t.setCompanyEmail("owner" + id + "@example.com");
        t.setStatus(TenantStatus.ACTIVE);
        t.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        t.setSubscriptionPlan("PRO");
        t.setBillingCycle(BillingCycle.MONTHLY);
        t.setSubscriptionEndDate(paidThrough);
        return t;
    }

    private void onFile(List<Tenant> list) {
        when(tenants.findByStatusAndSubscriptionStatusIn(any(), any())).thenReturn(list);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> varsSentTo(String email) {
        ArgumentCaptor<Map<String, Object>> vars = ArgumentCaptor.forClass(Map.class);
        verify(mail).sendSubscriptionNotice(eq(EmailTemplateKey.SUBSCRIPTION_EXPIRING), eq(email), anyString(),
                vars.capture());
        return vars.getValue();
    }

    @Test
    @DisplayName("7 days before, and 1 day before — once each")
    void remindersAtSevenAndOne() {
        Tenant week = tenant(1, TODAY.plusDays(7));
        Tenant tomorrow = tenant(2, TODAY.plusDays(1));
        Tenant farOff = tenant(3, TODAY.plusDays(10));
        onFile(List.of(week, tomorrow, farOff));

        assertThat(service.sendDueReminders(TODAY)).isEqualTo(2);

        assertThat(varsSentTo("owner1@example.com").get("endsIn")).isEqualTo("in 7 days");
        assertThat(varsSentTo("owner2@example.com").get("endsIn")).isEqualTo("tomorrow");
        verify(mail, never()).sendSubscriptionNotice(any(), eq("owner3@example.com"), anyString(), any());
    }

    @Test
    @DisplayName("the email carries the date, price, grace days and the link to renew")
    void emailContents() {
        onFile(List.of(tenant(1, TODAY.plusDays(7))));

        service.sendDueReminders(TODAY);

        Map<String, Object> vars = varsSentTo("owner1@example.com");
        assertThat(vars.get("paidThrough")).isEqualTo("29 Sep 2026");
        assertThat(vars.get("amount")).isEqualTo("USD 49.00");
        assertThat(vars.get("graceDays")).isEqualTo(3);
        assertThat(vars.get("accessEndsOn")).isEqualTo("2 Oct 2026");
        // The frontend's page — the old in-app links point at /client/subscription,
        // which is an API path, not a screen.
        assertThat(vars.get("renewLink")).isEqualTo("http://app.example/my-subscription");
    }

    @Test
    @DisplayName("already sent this period: not sent again, however often the job runs")
    void noRepeat() {
        Tenant t = tenant(1, TODAY.plusDays(7));
        onFile(List.of(t));
        when(sent.existsByTenantIdAndPaidThroughAndKind(1L, TODAY.plusDays(7), "BEFORE_7")).thenReturn(true);

        assertThat(service.sendDueReminders(TODAY)).isZero();
        verify(mail, never()).sendSubscriptionNotice(any(), any(), any(), any());
    }

    @Test
    @DisplayName("a missed morning catches up: 5 days left and no 7-day reminder yet → send it")
    void catchesUp() {
        onFile(List.of(tenant(1, TODAY.plusDays(5))));

        service.sendDueReminders(TODAY);

        ArgumentCaptor<SubscriptionReminder> row = ArgumentCaptor.forClass(SubscriptionReminder.class);
        verify(sent).save(row.capture());
        assertThat(row.getValue().getKind()).isEqualTo("BEFORE_7");
        assertThat(varsSentTo("owner1@example.com").get("endsIn")).isEqualTo("in 5 days");
    }

    @Test
    @DisplayName("ends today still gets the last reminder; already past gets none")
    void edges() {
        onFile(List.of(tenant(1, TODAY), tenant(2, TODAY.minusDays(1))));

        assertThat(service.sendDueReminders(TODAY)).isEqualTo(1);
        assertThat(varsSentTo("owner1@example.com").get("endsIn")).isEqualTo("today");
    }

    @Test
    @DisplayName("sent to the company address and active admins — not viewers, not twice")
    void recipients() {
        Tenant t = tenant(1, TODAY.plusDays(1));
        onFile(List.of(t));
        User admin = user("Asha@Example.com", AccessType.ADMIN, UserStatus.ACTIVE);
        User sameAsCompany = user("OWNER1@example.com", AccessType.ADMIN, UserStatus.ACTIVE);
        User viewer = user("viewer@example.com", AccessType.VIEWER, UserStatus.ACTIVE);
        User inactiveAdmin = user("gone@example.com", AccessType.ADMIN, UserStatus.INACTIVE);
        when(users.findByTenantId(1L)).thenReturn(List.of(admin, sameAsCompany, viewer, inactiveAdmin));

        service.sendDueReminders(TODAY);

        verify(mail, times(2)).sendSubscriptionNotice(any(), anyString(), anyString(), any());
        verify(mail).sendSubscriptionNotice(any(), eq("owner1@example.com"), anyString(), any());
        verify(mail).sendSubscriptionNotice(any(), eq("asha@example.com"), anyString(), any());
    }

    @Test
    @DisplayName("the expired notice goes once per period, and says the account is read-only")
    void expiredNotice() {
        Tenant t = tenant(1, TODAY.minusDays(4));

        assertThat(service.sendExpiredNotice(t)).isTrue();
        verify(mail).sendSubscriptionNotice(eq(EmailTemplateKey.SUBSCRIPTION_EXPIRED),
                eq("owner1@example.com"), anyString(), any());

        when(sent.existsByTenantIdAndPaidThroughAndKind(1L, TODAY.minusDays(4), "EXPIRED")).thenReturn(true);
        assertThat(service.sendExpiredNotice(t)).isFalse();
        verify(mail, times(1)).sendSubscriptionNotice(any(), any(), any(), any());
    }

    @Test
    @DisplayName("renewing starts a new period, so its reminders are sent afresh")
    void newPeriodNewReminders() {
        // Last period's 7-day reminder is on record — for the old date only.
        when(sent.existsByTenantIdAndPaidThroughAndKind(1L, TODAY.minusDays(23), "BEFORE_7")).thenReturn(true);
        onFile(List.of(tenant(1, TODAY.plusDays(7))));

        assertThat(service.sendDueReminders(TODAY)).isEqualTo(1);
    }

    private static User user(String email, AccessType type, UserStatus status) {
        User u = new User();
        u.setEmail(email);
        u.setFirstName("First");
        u.setLastName("Last");
        u.setAccessType(type);
        u.setStatus(status);
        return u;
    }
}
