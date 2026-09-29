package com.priceintel.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceintel.backend.constants.AccountAccess;
import com.priceintel.backend.constants.SubscriptionStatus;
import com.priceintel.backend.constants.TenantStatus;
import com.priceintel.backend.entity.Tenant;
import com.priceintel.backend.entity.User;
import com.priceintel.backend.repository.TenantRepository;
import com.priceintel.backend.security.AccountAccessPolicy;
import com.priceintel.backend.security.CustomUserDetails;
import com.priceintel.backend.security.SubscriptionAccessFilter;
import com.priceintel.backend.service.impl.SubscriptionAccessService;

/**
 * Who may do what once a plan runs out.
 *
 * <p>The companies below are the real ones on 22 Sep 2026, with their real
 * dates — including the two that the obvious rule gets wrong: Patuli, whose
 * trial ended but who paid for a year, and Price Matrix (2), which has sixteen
 * payments but whose last paid month ended on 17 Sep.</p>
 */
class SubscriptionAccessTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 22);
    private static final int GRACE = 3;

    private static Tenant tenant(TenantStatus status, SubscriptionStatus sub,
                                 LocalDate trialEnd, LocalDate paidThrough) {
        Tenant t = new Tenant();
        t.setId(1L);
        t.setStatus(status);
        t.setSubscriptionStatus(sub);
        t.setTrialEndDate(trialEnd);
        t.setSubscriptionEndDate(paidThrough);
        t.setSubscriptionPlan("PRO");
        return t;
    }

    private static AccountAccess access(Tenant t) {
        return SubscriptionAccessService.accessFor(t, TODAY, GRACE);
    }

    @Nested
    @DisplayName("the rule")
    class Rule {

        @Test
        @DisplayName("a trial that ended but was paid past keeps full access")
        void paidPastTrial() {
            // Patuli Infosys: trial to 24 Aug, paid for a year to 24 Aug 2027.
            // Judged by the trial date it would be locked out with eleven
            // months still paid for.
            Tenant patuli = tenant(TenantStatus.ACTIVE, SubscriptionStatus.TRIAL,
                    LocalDate.of(2026, 8, 24), LocalDate.of(2027, 8, 24));
            assertThat(access(patuli)).isEqualTo(AccountAccess.FULL);
        }

        @Test
        @DisplayName("past the paid-through date and the grace days: read-only")
        void lapsedIsReadOnly() {
            // Price Matrix (2): paid through 17 Sep; grace ran out on the 20th.
            Tenant pm2 = tenant(TenantStatus.ACTIVE, SubscriptionStatus.ACTIVE,
                    LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 17));
            assertThat(access(pm2)).isEqualTo(AccountAccess.READ_ONLY);
        }

        @Test
        @DisplayName("an unpaid trial past its end is read-only too")
        void lapsedTrial() {
            // Vertex Solutions: no payments, covered to 31 Aug.
            Tenant vertex = tenant(TenantStatus.ACTIVE, SubscriptionStatus.TRIAL,
                    LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
            assertThat(access(vertex)).isEqualTo(AccountAccess.READ_ONLY);
        }

        @Test
        @DisplayName("inside the grace days: still full, and the user is told how long is left")
        void graceDays() {
            Tenant late = tenant(TenantStatus.ACTIVE, SubscriptionStatus.ACTIVE,
                    null, LocalDate.of(2026, 9, 20));
            assertThat(access(late)).isEqualTo(AccountAccess.FULL);

            var status = SubscriptionAccessService.describe(late, TODAY, GRACE);
            assertThat(status.getGraceDaysLeft()).isEqualTo(1);
            assertThat(status.getAccessEndsOn()).isEqualTo(LocalDate.of(2026, 9, 23));
            assertThat(status.getMessage()).contains("Renew within 1 day");
        }

        @Test
        @DisplayName("the last grace day is still full access, and says so")
        void lastGraceDay() {
            Tenant edge = tenant(TenantStatus.ACTIVE, SubscriptionStatus.ACTIVE,
                    null, LocalDate.of(2026, 9, 19));   // + 3 = today
            assertThat(access(edge)).isEqualTo(AccountAccess.FULL);
            assertThat(SubscriptionAccessService.describe(edge, TODAY, GRACE).getMessage())
                    .contains("Renew today");
        }

        @Test
        @DisplayName("a suspended company is blocked, whatever its dates say")
        void suspended() {
            // WelcomeTest 7742.
            Tenant t = tenant(TenantStatus.SUSPENDED, SubscriptionStatus.TRIAL,
                    null, LocalDate.of(2026, 8, 1));
            assertThat(access(t)).isEqualTo(AccountAccess.BLOCKED);
            assertThat(SubscriptionAccessService.describe(t, TODAY, GRACE).getMessage())
                    .contains("suspended");
        }

        @Test
        @DisplayName("marked EXPIRED is read-only even if someone moved the date later")
        void storedExpiry() {
            Tenant t = tenant(TenantStatus.ACTIVE, SubscriptionStatus.EXPIRED, null, TODAY.plusDays(30));
            assertThat(access(t)).isEqualTo(AccountAccess.READ_ONLY);
        }

        @Test
        @DisplayName("cancelled, unpaid sign-ups and missing companies are blocked")
        void blocked() {
            assertThat(access(tenant(TenantStatus.ACTIVE, SubscriptionStatus.CANCELLED, null, TODAY.plusDays(9))))
                    .isEqualTo(AccountAccess.BLOCKED);
            assertThat(access(tenant(TenantStatus.PENDING_PAYMENT, SubscriptionStatus.TRIAL, null, null)))
                    .isEqualTo(AccountAccess.BLOCKED);
            assertThat(access(null)).isEqualTo(AccountAccess.BLOCKED);
        }

        @Test
        @DisplayName("no end date at all is open-ended, not expired")
        void openEnded() {
            assertThat(access(tenant(TenantStatus.ACTIVE, SubscriptionStatus.ACTIVE, null, null)))
                    .isEqualTo(AccountAccess.FULL);
        }
    }

    @Nested
    @DisplayName("what an expired company may still do")
    class Policy {

        private boolean readOnly(String method, String path) {
            return AccountAccessPolicy.allows(AccountAccess.READ_ONLY, method, path);
        }

        @Test
        @DisplayName("read its own data, export it, and sign in and out")
        void reads() {
            assertThat(readOnly("GET", "/api/v1/products")).isTrue();
            assertThat(readOnly("GET", "/api/v1/products/12/market-prices")).isTrue();
            assertThat(readOnly("GET", "/api/v1/products/export")).isTrue();
            assertThat(readOnly("GET", "/api/v1/client/subscription")).isTrue();
            assertThat(readOnly("POST", "/api/v1/auth/logout")).isTrue();
            assertThat(readOnly("POST", "/api/v1/auth/refresh")).isTrue();
            assertThat(readOnly("PUT", "/api/v1/profile")).isTrue();
            assertThat(readOnly("PATCH", "/api/v1/notifications/5/read")).isTrue();
        }

        @Test
        @DisplayName("pay to upgrade — the way back in")
        void upgrade() {
            assertThat(readOnly("POST", "/api/v1/client/subscription/upgrade")).isTrue();
        }

        @Test
        @DisplayName("not downgrade: a scheduled downgrade is applied with a free new period")
        void noDowngrade() {
            assertThat(readOnly("POST", "/api/v1/client/subscription/downgrade")).isFalse();
        }

        @Test
        @DisplayName("nothing that spends marketplace or AI quota")
        void noSpending() {
            assertThat(readOnly("POST", "/api/v1/search")).isFalse();
            assertThat(readOnly("POST", "/api/v1/marketplaces/search")).isFalse();
            assertThat(readOnly("POST", "/api/v1/products/12/ai-review")).isFalse();
            assertThat(readOnly("POST", "/api/v1/ai/analyst/ask")).isFalse();
            // GETs that are live marketplace calls, not reads of stored data.
            assertThat(readOnly("GET", "/api/v1/marketplaces/AMAZON/listings/B08RC6981L")).isFalse();
            assertThat(readOnly("GET", "/api/v1/marketplaces/EBAY/listings/123/other-sellers")).isFalse();
            assertThat(readOnly("GET", "/api/v1/marketplaces/amazon/products/B08RC6981L/raw")).isFalse();
        }

        @Test
        @DisplayName("no new products, monitors, alerts or users")
        void noWrites() {
            assertThat(readOnly("POST", "/api/v1/products")).isFalse();
            assertThat(readOnly("PUT", "/api/v1/products/12")).isFalse();
            assertThat(readOnly("POST", "/api/v1/monitors")).isFalse();
            assertThat(readOnly("POST", "/api/v1/alerts/rules")).isFalse();
            assertThat(readOnly("POST", "/api/v1/users")).isFalse();
        }

        @Test
        @DisplayName("a blocked company may only find out why, and sign out")
        void blocked() {
            assertThat(AccountAccessPolicy.allows(AccountAccess.BLOCKED, "GET", "/api/v1/auth/me")).isTrue();
            assertThat(AccountAccessPolicy.allows(AccountAccess.BLOCKED, "POST", "/api/v1/auth/logout")).isTrue();
            assertThat(AccountAccessPolicy.allows(AccountAccess.BLOCKED, "GET", "/api/v1/products")).isFalse();
            assertThat(AccountAccessPolicy.allows(AccountAccess.BLOCKED, "POST", "/api/v1/client/subscription/upgrade"))
                    .isFalse();
        }

        @Test
        @DisplayName("full access is never restricted, and public pages are not tenant work")
        void fullAndPublic() {
            assertThat(AccountAccessPolicy.allows(AccountAccess.FULL, "POST", "/api/v1/search")).isTrue();
            assertThat(AccountAccessPolicy.allows(AccountAccess.BLOCKED, "GET", "/api/public/pricing")).isTrue();
        }
    }

    @Nested
    @DisplayName("the request check")
    class Filter {

        private final TenantRepository repo = mock(TenantRepository.class);
        private final SubscriptionAccessService service = new SubscriptionAccessService(repo, GRACE);
        private final SubscriptionAccessFilter filter = new SubscriptionAccessFilter(service, new ObjectMapper());

        @AfterEach
        void clear() {
            SecurityContextHolder.clearContext();
        }

        private void signIn(Long tenantId, boolean superAdmin) {
            User u = new User();
            u.setId(9L);
            u.setEmail("someone@example.com");
            u.setTenantId(tenantId);
            u.setSuperAdmin(superAdmin);
            CustomUserDetails details = new CustomUserDetails(u);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
        }

        private MockHttpServletResponse call(String method, String path) throws Exception {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(new MockHttpServletRequest(method, path), response, new MockFilterChain());
            return response;
        }

        @Test
        @DisplayName("an expired company's search is refused with a code the screen can act on")
        void refusesExpiredSearch() throws Exception {
            when(repo.findById(anyLong())).thenReturn(Optional.of(tenant(TenantStatus.ACTIVE,
                    SubscriptionStatus.ACTIVE, null, LocalDate.now().minusDays(30))));
            signIn(7L, false);

            MockHttpServletResponse response = call("POST", "/api/v1/search");

            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getContentAsString())
                    .contains("\"code\":\"SUBSCRIPTION_EXPIRED\"")
                    .contains("\"access\":\"READ_ONLY\"");
        }

        @Test
        @DisplayName("but it can still read its products")
        void letsExpiredRead() throws Exception {
            when(repo.findById(anyLong())).thenReturn(Optional.of(tenant(TenantStatus.ACTIVE,
                    SubscriptionStatus.ACTIVE, null, LocalDate.now().minusDays(30))));
            signIn(7L, false);

            assertThat(call("GET", "/api/v1/products").getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("a company suspended mid-session is stopped on its next request")
        void stopsSuspendedSession() throws Exception {
            when(repo.findById(anyLong())).thenReturn(Optional.of(tenant(TenantStatus.SUSPENDED,
                    SubscriptionStatus.ACTIVE, null, LocalDate.now().plusDays(300))));
            signIn(7L, false);

            MockHttpServletResponse response = call("GET", "/api/v1/products");

            assertThat(response.getStatus()).isEqualTo(403);
            assertThat(response.getContentAsString()).contains("\"code\":\"ACCOUNT_BLOCKED\"");
        }

        @Test
        @DisplayName("the platform owner is never checked")
        void superAdminPasses() throws Exception {
            signIn(null, true);
            assertThat(call("POST", "/api/v1/search").getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("a renewal is seen on the next request, not after the cache expires")
        void evictionIsImmediate() throws Exception {
            Tenant t = tenant(TenantStatus.ACTIVE, SubscriptionStatus.ACTIVE, null, LocalDate.now().minusDays(30));
            when(repo.findById(any())).thenReturn(Optional.of(t));
            signIn(7L, false);
            assertThat(call("POST", "/api/v1/search").getStatus()).isEqualTo(403);

            t.setSubscriptionEndDate(LocalDate.now().plusDays(30));   // paid
            service.evict(7L);

            assertThat(call("POST", "/api/v1/search").getStatus()).isEqualTo(200);
        }
    }
}
