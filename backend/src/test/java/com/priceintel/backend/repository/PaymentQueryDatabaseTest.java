package com.priceintel.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import com.priceintel.backend.constants.PaymentStatus;
import com.priceintel.backend.constants.PaymentType;

/**
 * The payment queries run against the real database.
 *
 * <p>These exist because the filters shipped broken and the unit tests were all
 * green: they mock the repository, so they prove the service asks for the right
 * window and prove nothing about whether the database will answer. PostgreSQL
 * cannot infer a type for a parameter it only ever sees inside {@code ? IS
 * NULL}, so every unfiltered request failed with "could not determine data type
 * of parameter $7" — at run time, on the first call, not at startup.</p>
 *
 * <p>Each case here is one combination of filters left out, which is exactly
 * what an untyped null parameter needs to surface. They assert no rows, only
 * that the database accepts the query: the data on a dev machine changes, the
 * shape of the SQL does not.</p>
 */
@SpringBootTest
class PaymentQueryDatabaseTest {

    @Autowired PaymentRepository payments;

    private static final long TENANT = 62L;
    private static final LocalDateTime OCT = LocalDateTime.parse("2026-10-01T00:00");
    private static final LocalDateTime NOV = LocalDateTime.parse("2026-11-01T00:00");

    @Test
    @DisplayName("no filters at all — every parameter null")
    void noFilters() {
        assertThatCode(() -> payments.search(null, null, null, null, null, PageRequest.of(0, 20)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("one client, no other filter")
    void tenantOnly() {
        assertThatCode(() -> payments.search(TENANT, null, null, null, null, PageRequest.of(0, 20)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a month, with no status or type — the first failure reported")
    void monthOnly() {
        assertThatCode(() -> payments.search(TENANT, null, null, OCT, NOV, PageRequest.of(0, 20)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an open-ended range, one side null")
    void halfOpenRange() {
        assertThatCode(() -> payments.search(TENANT, null, null, OCT, null, PageRequest.of(0, 20)))
                .doesNotThrowAnyException();
        assertThatCode(() -> payments.search(TENANT, null, null, null, NOV, PageRequest.of(0, 20)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("every filter given at once")
    void allFilters() {
        assertThatCode(() -> payments.search(TENANT, PaymentStatus.PAID, PaymentType.UPGRADE,
                OCT, NOV, PageRequest.of(0, 20)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the totals query takes the same filters, present or absent")
    void summariseAcceptsTheSameShapes() {
        assertThatCode(() -> payments.summarise(null, null, null, null, null))
                .doesNotThrowAnyException();
        assertThatCode(() -> payments.summarise(TENANT, PaymentStatus.PAID, null, OCT, NOV))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the months query answers for one client and for all of them")
    void monthsForClientAndForEveryone() {
        // The null case is the platform owner's view, and the one a native
        // query is most likely to reject.
        assertThat(payments.monthsWithPayments(null)).isNotNull();
        assertThat(payments.monthsWithPayments(TENANT)).isNotNull();
    }

    @Test
    @DisplayName("months come back as YYYY-MM, newest first")
    void monthsAreOrderedAndFormatted() {
        var months = payments.monthsWithPayments(null);
        assertThat(months).allMatch(m -> m.matches("\\d{4}-\\d{2}"));
        assertThat(months).isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }
}
