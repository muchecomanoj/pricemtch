package com.priceintel.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.priceintel.backend.constants.PaymentStatus;
import com.priceintel.backend.constants.PaymentType;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.repository.PaymentRepository;
import com.priceintel.backend.service.impl.SubscriptionServiceImpl;

/**
 * Filtering the payment history, and the figures above it.
 *
 * <p>The page shows months of upgrades, retries and abandoned checkouts mixed
 * together. The rules worth protecting: a month means the month the rows are
 * dated, a total never sums two currencies, and the month picker keeps its
 * options whatever else is filtered.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentHistoryFilterTest {

    @Mock PaymentRepository paymentRepository;

    @InjectMocks SubscriptionServiceImpl service;

    private static final long TENANT = 62L;

    @BeforeEach
    void setUp() {
        when(paymentRepository.search(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());
        when(paymentRepository.summarise(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(paymentRepository.monthsWithPayments(any()))
                .thenReturn(List.of("2026-10", "2026-08", "2026-07"));
    }

    /** The window the repository was actually asked for. */
    private LocalDateTime[] windowAsked() {
        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(paymentRepository).search(any(), any(), any(),
                from.capture(), to.capture(), any(Pageable.class));
        return new LocalDateTime[] { from.getValue(), to.getValue() };
    }

    private Object[] group(PaymentStatus status, String currency, long count, String total) {
        return new Object[] { status, currency, count, new BigDecimal(total) };
    }

    @Nested
    @DisplayName("choosing a month")
    class Month {

        @Test
        @DisplayName("October means the whole of October and nothing of November")
        void monthBecomesAHalfOpenWindow() {
            service.paymentHistory(TENANT, null, null, "2026-10", null, null, 0, 20);

            LocalDateTime[] window = windowAsked();
            assertThat(window[0]).isEqualTo(LocalDateTime.parse("2026-10-01T00:00"));
            // Exclusive end. An inclusive one would have to name the last
            // microsecond of the month to keep a payment made at 23:59.
            assertThat(window[1]).isEqualTo(LocalDateTime.parse("2026-11-01T00:00"));
        }

        @Test
        @DisplayName("December rolls into the next year rather than month 13")
        void decemberRollsOver() {
            service.paymentHistory(TENANT, null, null, "2026-12", null, null, 0, 20);

            assertThat(windowAsked()[1]).isEqualTo(LocalDateTime.parse("2027-01-01T00:00"));
        }

        @Test
        @DisplayName("no month means no date window at all")
        void noMonthMeansEverything() {
            service.paymentHistory(TENANT, null, null, null, null, null, 0, 20);

            LocalDateTime[] window = windowAsked();
            assertThat(window[0]).isNull();
            assertThat(window[1]).isNull();
        }

        @Test
        @DisplayName("something that is not a month is refused, not quietly ignored")
        void badMonthIsRefused() {
            // Ignoring it would show the whole history under a heading that
            // says October.
            assertThatThrownBy(() ->
                    service.paymentHistory(TENANT, null, null, "October", null, null, 0, 20))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Use YYYY-MM");
        }
    }

    @Nested
    @DisplayName("an explicit date range")
    class Range {

        @Test
        @DisplayName("includes both days named")
        void endDayIsIncluded() {
            service.paymentHistory(TENANT, null, null, null,
                    LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"), 0, 20);

            LocalDateTime[] window = windowAsked();
            assertThat(window[0]).isEqualTo(LocalDateTime.parse("2026-10-01T00:00"));
            // A payment made on the evening of the 31st is inside the range the
            // user asked for.
            assertThat(window[1]).isEqualTo(LocalDateTime.parse("2026-11-01T00:00"));
        }

        @Test
        @DisplayName("wins over a month, being the more specific instruction")
        void rangeOverridesMonth() {
            service.paymentHistory(TENANT, null, null, "2026-08",
                    LocalDate.parse("2026-10-05"), null, 0, 20);

            LocalDateTime[] window = windowAsked();
            assertThat(window[0]).isEqualTo(LocalDateTime.parse("2026-10-05T00:00"));
            assertThat(window[1]).isNull();
        }
    }

    @Nested
    @DisplayName("the figures above the table")
    class Totals {

        @Test
        @DisplayName("count paid and pending apart, and total only what was paid")
        void paidAndPendingAreSeparate() {
            when(paymentRepository.summarise(any(), any(), any(), any(), any()))
                    .thenReturn(List.of(
                            group(PaymentStatus.PAID, "USD", 14, "2774.40"),
                            group(PaymentStatus.PENDING, "USD", 2, "298.00")));

            var summary = service.paymentHistory(TENANT, null, null, null, null, null, 0, 20)
                    .getSummary();

            // A pending row is a checkout that was started and never finished.
            // Adding it to the total would overstate what was charged.
            assertThat(summary.getPaidAmount()).isEqualByComparingTo("2774.40");
            assertThat(summary.getCurrency()).isEqualTo("USD");
            assertThat(summary.getPaidCount()).isEqualTo(14);
            assertThat(summary.getPendingCount()).isEqualTo(2);
            assertThat(summary.getTotalCount()).isEqualTo(16);
            assertThat(summary.isMixedCurrencies()).isFalse();
        }

        @Test
        @DisplayName("withhold the total when two currencies are in the set")
        void mixedCurrenciesAreNotSummed() {
            when(paymentRepository.summarise(any(), any(), any(), any(), any()))
                    .thenReturn(List.of(
                            group(PaymentStatus.PAID, "USD", 3, "447.00"),
                            group(PaymentStatus.PAID, "GBP", 2, "260.00")));

            var summary = service.paymentHistory(TENANT, null, null, null, null, null, 0, 20)
                    .getSummary();

            // 447 + 260 = 707 of nothing. The counts stay true.
            assertThat(summary.getPaidAmount()).isNull();
            assertThat(summary.getCurrency()).isNull();
            assertThat(summary.isMixedCurrencies()).isTrue();
            assertThat(summary.getPaidCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("an empty result is zeroes, not nulls")
        void emptyIsZero() {
            var summary = service.paymentHistory(TENANT, null, null, "2026-01", null, null, 0, 20)
                    .getSummary();

            assertThat(summary.getTotalCount()).isZero();
            assertThat(summary.getPaidCount()).isZero();
            assertThat(summary.getPaidAmount()).isNull();
        }
    }

    @Nested
    @DisplayName("the month picker's options")
    class Months {

        @Test
        @DisplayName("are the months that hold a payment, newest first")
        void monthsComeFromTheData() {
            var result = service.paymentHistory(TENANT, null, null, null, null, null, 0, 20);

            assertThat(result.getMonths()).containsExactly("2026-10", "2026-08", "2026-07");
        }

        @Test
        @DisplayName("are not narrowed by the filters the user is about to change")
        void monthsIgnoreTheOtherFilters() {
            service.paymentHistory(TENANT, PaymentStatus.PAID, PaymentType.RENEWAL,
                    "2026-10", null, null, 0, 20);

            // Narrowing them would make a month vanish from the picker the
            // moment it held nothing of the chosen status — leaving no way back.
            verify(paymentRepository).monthsWithPayments(TENANT);
        }
    }

    @Nested
    @DisplayName("who sees what")
    class Scope {

        @Test
        @DisplayName("a client's own history is scoped to that client")
        void tenantIsPassedThrough() {
            service.paymentHistory(TENANT, null, null, null, null, null, 0, 20);

            verify(paymentRepository).search(eq(TENANT), any(), any(), any(), any(),
                    any(Pageable.class));
        }

        @Test
        @DisplayName("a null tenant is the platform owner, who sees every client")
        void nullTenantIsEveryone() {
            service.paymentHistory(null, null, null, null, null, null, 0, 20);

            verify(paymentRepository).search(isNull(), any(), any(), any(), any(),
                    any(Pageable.class));
            verify(paymentRepository).monthsWithPayments(isNull());
        }

        @Test
        @DisplayName("status and type reach the query as given")
        void filtersReachTheQuery() {
            service.paymentHistory(TENANT, PaymentStatus.PAID, PaymentType.UPGRADE,
                    null, null, null, 0, 20);

            verify(paymentRepository).search(eq(TENANT), eq(PaymentStatus.PAID),
                    eq(PaymentType.UPGRADE), isNull(), isNull(), any(Pageable.class));
        }
    }
}
