package com.priceintel.backend.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.priceintel.backend.entity.FxRate;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.exception.ResourceNotFoundException;
import com.priceintel.backend.repository.FxRateRepository;
import com.priceintel.backend.repository.TenantRepository;
import com.priceintel.backend.security.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Converts money between currencies at a stored rate.
 *
 * <p>Every price in the system already carried a currency code; nothing used
 * it. A Canadian listing at C$95 and a US listing at $70 were sorted together
 * and their median labelled with whichever currency came first — a number that
 * looked authoritative, meant nothing, and fed the recommendation engine.</p>
 *
 * <p>The rule here is that a figure is either converted at a rate the caller
 * can see, or it is left out and said to be left out. Silently mixing is the
 * one behaviour this class exists to remove.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FxRateService {

    /** Enough places that repeated conversion of small amounts does not drift. */
    private static final int WORKING_SCALE = 6;

    private final FxRateRepository repo;
    private final TenantRepository tenantRepo;

    /**
     * The outcome of a conversion.
     *
     * @param amount   the converted amount, or null when no rate was available
     * @param currency what {@code amount} is denominated in
     * @param rate     the rate used; null when none was needed or none existed
     * @param asOf     when that rate was true
     * @param source   where the rate came from, for display beside the figure
     */
    public record Converted(BigDecimal amount, String currency, BigDecimal rate,
                            Instant asOf, String source) {

        /** True when a rate was actually applied, as opposed to a same-currency pass-through. */
        public boolean wasConverted() {
            return rate != null;
        }

        /** True when conversion was needed but no rate existed — the figure is unusable. */
        public boolean isUnavailable() {
            return amount == null;
        }
    }

    // ---------- reporting currency ----------

    /**
     * The currency this client's figures should be presented in, or null when
     * they have not set one.
     *
     * <p>Null is left as null rather than defaulted to USD: guessing produces a
     * screen full of dollar signs on Canadian data, which is worse than a screen
     * that admits it does not know.</p>
     */
    @Transactional(readOnly = true)
    public String reportingCurrency() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return null;
        }
        return tenantRepo.findById(tenantId)
                .map(t -> normalise(t.getCurrency()))
                .orElse(null);
    }

    // ---------- conversion ----------

    /** Converts at the newest rate that was already true. */
    public Converted convert(BigDecimal amount, String from, String to) {
        return convert(amount, from, to, Instant.now());
    }

    /**
     * Converts {@code amount} from one currency to another as at a moment.
     *
     * <p>A missing rate returns a null amount rather than the unconverted
     * number. Passing the original through would produce a figure that is wrong
     * by the whole exchange rate while looking entirely normal.</p>
     */
    public Converted convert(BigDecimal amount, String from, String to, Instant at) {
        String base = normalise(from);
        String quote = normalise(to);
        if (amount == null) {
            return new Converted(null, quote != null ? quote : base, null, null, null);
        }
        // Nothing to convert: unknown target, unknown source, or already there.
        if (base == null || quote == null || base.equals(quote)) {
            return new Converted(amount, base != null ? base : quote, null, null, null);
        }

        FxRate rate = findRate(base, quote, at);
        if (rate != null) {
            return new Converted(
                    amount.multiply(rate.getRate()).setScale(2, RoundingMode.HALF_UP),
                    quote, rate.getRate(), rate.getAsOf(), rate.getSource());
        }

        // The inverse pair, so storing USD→CAD does not also require CAD→USD.
        // Same fact, written the other way round.
        FxRate inverse = findRate(quote, base, at);
        if (inverse != null) {
            BigDecimal impliedRate = BigDecimal.ONE.divide(
                    inverse.getRate(), WORKING_SCALE, RoundingMode.HALF_UP);
            return new Converted(
                    amount.multiply(impliedRate).setScale(2, RoundingMode.HALF_UP),
                    quote, impliedRate, inverse.getAsOf(), inverse.getSource());
        }

        log.debug("No {}->{} rate as of {}; leaving the figure unconverted", base, quote, at);
        return new Converted(null, quote, null, null, null);
    }

    /** Whether a figure in this currency can be shown in the reporting currency. */
    public boolean canConvert(String from, String to) {
        String base = normalise(from);
        String quote = normalise(to);
        if (base == null || quote == null || base.equals(quote)) {
            return true;
        }
        Instant now = Instant.now();
        return findRate(base, quote, now) != null || findRate(quote, base, now) != null;
    }

    private FxRate findRate(String base, String quote, Instant at) {
        List<FxRate> found = repo.findLatestAtOrBefore(base, quote, at, PageRequest.of(0, 1));
        return found.isEmpty() ? null : found.get(0);
    }

    // ---------- maintenance ----------

    @Transactional
    public FxRate save(FxRate incoming) {
        String base = normalise(incoming.getBaseCurrency());
        String quote = normalise(incoming.getQuoteCurrency());
        if (base == null || quote == null) {
            throw new BadRequestException("Both currencies are required, as three-letter codes.");
        }
        if (base.equals(quote)) {
            throw new BadRequestException(
                    "A currency's rate against itself is always 1 and is not stored.");
        }
        if (incoming.getRate() == null || incoming.getRate().signum() <= 0) {
            throw new BadRequestException("The rate must be greater than zero.");
        }
        incoming.setBaseCurrency(base);
        incoming.setQuoteCurrency(quote);
        if (incoming.getAsOf() == null) {
            incoming.setAsOf(Instant.now());
        }
        if (incoming.getSource() == null || incoming.getSource().isBlank()) {
            incoming.setSource("MANUAL");
        }

        // Re-stating a rate for a moment already recorded updates it rather than
        // failing on the unique index — correcting a typo should not require
        // finding and deleting the row first.
        FxRate row = repo.findByBaseCurrencyAndQuoteCurrencyAndAsOf(base, quote, incoming.getAsOf())
                .orElse(incoming);
        row.setRate(incoming.getRate());
        row.setSource(incoming.getSource());
        row.setBaseCurrency(base);
        row.setQuoteCurrency(quote);
        row.setAsOf(incoming.getAsOf());
        log.info("Stored FX rate {}->{} = {} as of {} ({})",
                base, quote, row.getRate(), row.getAsOf(), row.getSource());
        return repo.save(row);
    }

    @Transactional(readOnly = true)
    public List<FxRate> list(int limit) {
        return repo.findAllByOrderByAsOfDesc(PageRequest.of(0, Math.min(Math.max(limit, 1), 500)));
    }

    @Transactional
    public void delete(Long id) {
        FxRate rate = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FX rate not found: " + id));
        repo.delete(rate);
    }

    /** Upper-cased and trimmed, or null when it is not a usable code. */
    private static String normalise(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim().toUpperCase();
        return trimmed.length() == 3 ? trimmed : null;
    }
}
