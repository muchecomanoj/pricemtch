package com.priceintel.backend.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One exchange rate, at one moment, from one source.
 *
 * <p>Rates are stored rather than looked up when a figure is displayed. A
 * report run twice on the same data has to give the same answer, and a live
 * lookup would restate an approved margin every time the market moved.</p>
 *
 * <p>Not tenant-scoped: an exchange rate is a fact about the world, and giving
 * every client their own copy would mean two clients disagreeing about what a
 * euro was worth on a Tuesday.</p>
 */
@Entity
@Table(name = "fx_rates",
        indexes = @Index(name = "idx_fx_rate_lookup",
                columnList = "base_currency, quote_currency, as_of"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FxRate extends BaseEntity {

    /** The currency being priced. */
    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    /** The currency it is priced in. */
    @Column(name = "quote_currency", nullable = false, length = 3)
    private String quoteCurrency;

    /** One unit of base costs this many units of quote. */
    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal rate;

    /**
     * When the rate was true — not when the row was written.
     *
     * <p>The two differ whenever a rate is backfilled, and it is this one that a
     * historical figure must be converted at.</p>
     */
    @Column(name = "as_of", nullable = false)
    private Instant asOf;

    /**
     * 'MANUAL' or a provider name.
     *
     * <p>Travels with the converted figure: "converted at a rate somebody typed
     * in" and "converted at the ECB close" deserve different trust.</p>
     */
    @Builder.Default
    @Column(nullable = false, length = 40)
    private String source = "MANUAL";
}
