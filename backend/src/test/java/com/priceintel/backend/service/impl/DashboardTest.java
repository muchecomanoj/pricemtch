package com.priceintel.backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.priceintel.backend.security.TenantContext;

/**
 * The Dashboard's figures.
 *
 * <p>The SQL itself is exercised against the real schema by the application
 * context test; these cover the judgement calls around it — what counts as
 * missing, how a change is expressed, and that a caller with no company is
 * shown nothing rather than the platform's totals.</p>
 */
class DashboardTest {

    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final DashboardService service = new DashboardService(jdbc);

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("no company")
    class NoCompany {

        @Test
        @DisplayName("the platform owner sees empty panels, not everyone's totals")
        void emptyForNoTenant() {
            TenantContext.set(null, true);

            assertThat(service.summary()).isEmpty();
            assertThat(service.activities().getSearches()).isEmpty();
            assertThat(service.charts().getPriceTrend()).isNull();
            // Nothing was asked of the database at all.
            verify(jdbc, never()).query(anyString(), any(java.util.Map.class),
                    any(org.springframework.jdbc.core.RowMapper.class));
        }
    }

    @Nested
    @DisplayName("change against the previous period")
    class Delta {

        @Test
        @DisplayName("a rise and a fall are both reported, to one decimal")
        void risesAndFalls() {
            assertThat(DashboardService.delta(120, 100)).isEqualTo(20.0);
            assertThat(DashboardService.delta(90, 100)).isEqualTo(-10.0);
            assertThat(DashboardService.delta(107, 93)).isEqualTo(15.1);
        }

        @Test
        @DisplayName("from nothing, there is no percentage to report")
        void fromZero() {
            // A company in its first month has no "last month". 100% growth
            // from zero reads as real growth; absent reads as what it is.
            assertThat(DashboardService.delta(12, 0)).isNull();
            assertThat(DashboardService.delta(0, 0)).isNull();
        }
    }

    @Nested
    @DisplayName("how things are labelled")
    class Labels {

        @Test
        @DisplayName("rule names are written for people")
        void readable() {
            assertThat(DashboardService.readable("PRICE_DROP")).isEqualTo("Price Drop");
            assertThat(DashboardService.readable("MARGIN_BREACH")).isEqualTo("Margin Breach");
            assertThat(DashboardService.readable("ASIN")).isEqualTo("Asin");
            assertThat(DashboardService.readable(null)).isNull();
        }

        @Test
        @DisplayName("an alert that costs money today reads louder than one that might")
        void severity() {
            assertThat(DashboardService.severity("PRICE_DROP")).isEqualTo("danger");
            assertThat(DashboardService.severity("MARGIN_BREACH")).isEqualTo("danger");
            assertThat(DashboardService.severity("NEW_SELLER")).isEqualTo("warning");
            assertThat(DashboardService.severity("PRICE_INCREASE")).isEqualTo("info");
            assertThat(DashboardService.severity(null)).isEqualTo("info");
        }

        @Test
        @DisplayName("risk follows the model's own confidence")
        void risk() {
            assertThat(DashboardService.risk(new BigDecimal("95"))).isEqualTo("Low");
            assertThat(DashboardService.risk(new BigDecimal("80"))).isEqualTo("Low");
            assertThat(DashboardService.risk(new BigDecimal("55"))).isEqualTo("Medium");
            assertThat(DashboardService.risk(new BigDecimal("49"))).isEqualTo("High");
            // No confidence recorded is not the same as a confident suggestion.
            assertThat(DashboardService.risk(null)).isEqualTo("High");
        }

        @Test
        @DisplayName("times read the way the screen shows them")
        void ago() {
            Instant now = Instant.now();
            assertThat(DashboardService.ago(now)).isEqualTo("just now");
            assertThat(DashboardService.ago(now.minus(2, ChronoUnit.MINUTES))).isEqualTo("2 min ago");
            assertThat(DashboardService.ago(now.minus(1, ChronoUnit.HOURS))).isEqualTo("1 hour ago");
            assertThat(DashboardService.ago(now.minus(5, ChronoUnit.HOURS))).isEqualTo("5 hours ago");
            assertThat(DashboardService.ago(now.minus(3, ChronoUnit.DAYS))).isEqualTo("3 days ago");
            assertThat(DashboardService.ago(null)).isNull();
        }
    }
}
