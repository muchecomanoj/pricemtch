package com.priceintel.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.priceintel.backend.entity.FxRate;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.repository.FxRateRepository;
import com.priceintel.backend.repository.TenantRepository;
import com.priceintel.backend.service.impl.FxRateService;

/**
 * Converting between currencies.
 *
 * <p>The case that matters most is the one where no rate exists: the figure has
 * to come back missing rather than unconverted, because an unconverted number
 * is wrong by the whole exchange rate and looks entirely normal.</p>
 */
class FxRateServiceTest {

    private FxRateRepository repo;
    private FxRateService service;

    private static final Instant AS_OF = Instant.parse("2026-09-01T00:00:00Z");

    @BeforeEach
    void setUp() {
        repo = mock(FxRateRepository.class);
        service = new FxRateService(repo, mock(TenantRepository.class));
    }

    private void haveRate(String base, String quote, String rate) {
        when(repo.findLatestAtOrBefore(eq(base), eq(quote), any(), any()))
                .thenReturn(List.of(FxRate.builder()
                        .baseCurrency(base).quoteCurrency(quote)
                        .rate(new BigDecimal(rate)).asOf(AS_OF).source("MANUAL").build()));
    }

    private void haveNoRates() {
        when(repo.findLatestAtOrBefore(any(), any(), any(), any())).thenReturn(List.of());
    }

    @Test
    @DisplayName("same currency passes straight through without needing a rate")
    void sameCurrencyNeedsNoRate() {
        haveNoRates();

        var result = service.convert(new BigDecimal("70.00"), "USD", "USD");

        assertThat(result.amount()).isEqualByComparingTo("70.00");
        assertThat(result.wasConverted()).isFalse();
        assertThat(result.isUnavailable()).isFalse();
    }

    @Test
    @DisplayName("converts at the stored rate and reports which rate it used")
    void convertsAtStoredRate() {
        haveNoRates();
        haveRate("CAD", "USD", "0.74");

        var result = service.convert(new BigDecimal("95.00"), "CAD", "USD");

        assertThat(result.amount()).isEqualByComparingTo("70.30");
        assertThat(result.currency()).isEqualTo("USD");
        assertThat(result.rate()).isEqualByComparingTo("0.74");
        assertThat(result.asOf()).isEqualTo(AS_OF);
        assertThat(result.source()).isEqualTo("MANUAL");
        assertThat(result.wasConverted()).isTrue();
    }

    @Test
    @DisplayName("uses the inverse of a stored rate, so one direction is enough")
    void usesInverseRate() {
        haveNoRates();
        haveRate("USD", "CAD", "1.35");

        var result = service.convert(new BigDecimal("135.00"), "CAD", "USD");

        // 1 / 1.35 = 0.740741; 135 * that = 100.00
        assertThat(result.amount()).isEqualByComparingTo("100.00");
        assertThat(result.wasConverted()).isTrue();
    }

    @Test
    @DisplayName("with no rate the amount is missing, not left unconverted")
    void missingRateYieldsNoAmount() {
        haveNoRates();

        var result = service.convert(new BigDecimal("95.00"), "CAD", "USD");

        assertThat(result.amount()).isNull();
        assertThat(result.isUnavailable()).isTrue();
        // The target is still reported, so the caller can say what it wanted.
        assertThat(result.currency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("an unknown currency on either side is a pass-through, not a wrong conversion")
    void unknownCurrencyPassesThrough() {
        haveNoRates();

        assertThat(service.convert(new BigDecimal("70.00"), null, "USD").amount())
                .isEqualByComparingTo("70.00");
        assertThat(service.convert(new BigDecimal("70.00"), "USD", null).amount())
                .isEqualByComparingTo("70.00");
        // Not a currency code.
        assertThat(service.convert(new BigDecimal("70.00"), "DOLLARS", "USD").amount())
                .isEqualByComparingTo("70.00");
    }

    @Test
    @DisplayName("codes are matched case-insensitively and trimmed")
    void codesAreNormalised() {
        haveNoRates();
        haveRate("CAD", "USD", "0.74");

        assertThat(service.convert(new BigDecimal("100.00"), " cad ", "usd").amount())
                .isEqualByComparingTo("74.00");
    }

    @Test
    @DisplayName("a null amount stays null")
    void nullAmount() {
        haveNoRates();

        assertThat(service.convert(null, "CAD", "USD").amount()).isNull();
    }

    @Test
    @DisplayName("canConvert is true for same currency even with no rates at all")
    void canConvertSameCurrency() {
        haveNoRates();

        assertThat(service.canConvert("USD", "USD")).isTrue();
        assertThat(service.canConvert("CAD", "USD")).isFalse();
    }

    // ---------- validation ----------

    @Test
    @DisplayName("a rate of a currency against itself is refused — it is always 1")
    void selfRateRefused() {
        assertThatThrownBy(() -> service.save(FxRate.builder()
                .baseCurrency("USD").quoteCurrency("USD").rate(BigDecimal.ONE).build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("against itself");
    }

    @Test
    @DisplayName("a zero or negative rate is refused")
    void nonPositiveRateRefused() {
        assertThatThrownBy(() -> service.save(FxRate.builder()
                .baseCurrency("CAD").quoteCurrency("USD").rate(BigDecimal.ZERO).build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    @DisplayName("a missing or malformed currency code is refused")
    void badCodeRefused() {
        assertThatThrownBy(() -> service.save(FxRate.builder()
                .baseCurrency("DOLLARS").quoteCurrency("USD").rate(BigDecimal.ONE).build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("three-letter");
    }
}
