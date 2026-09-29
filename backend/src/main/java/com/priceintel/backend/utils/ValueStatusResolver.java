package com.priceintel.backend.utils;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.priceintel.backend.constants.ValueStatus;

/**
 * Decides which {@link ValueStatus} a number carries.
 *
 * <p>Kept in one place so the rules cannot drift between the screens that show
 * a figure and the export that ships it.</p>
 */
@Component
public class ValueStatusResolver {

    /**
     * How old an observation may be before it is reported as stale.
     *
     * <p>Defaults to the competitor cache TTL: past that point the system would
     * itself refuse to reuse the figure, so it should not present it as current
     * either.</p>
     */
    @Value("${app.value-status.stale-after-hours:48}")
    private long staleAfterHours;

    /**
     * A price read from a marketplace: actual while fresh, stale once the
     * observation is older than the threshold, unavailable when absent.
     */
    public ValueStatus forObservation(Object value, Instant observedAt) {
        if (value == null) {
            return ValueStatus.UNAVAILABLE;
        }
        if (observedAt == null) {
            // A value with no recorded observation time cannot be vouched for
            // as current, and saying so is better than implying freshness.
            return ValueStatus.STALE;
        }
        return observedAt.isBefore(Instant.now().minus(Duration.ofHours(staleAfterHours)))
                ? ValueStatus.STALE
                : ValueStatus.ACTUAL;
    }

    /**
     * A figure produced by arithmetic. Inherits staleness from its inputs — a
     * margin computed from a three-day-old price is not a current margin.
     */
    public ValueStatus forCalculation(Object value, ValueStatus inputStatus) {
        if (value == null) {
            return ValueStatus.UNAVAILABLE;
        }
        if (inputStatus == ValueStatus.STALE || inputStatus == ValueStatus.UNAVAILABLE) {
            return inputStatus == ValueStatus.UNAVAILABLE ? ValueStatus.CALCULATED : ValueStatus.STALE;
        }
        return ValueStatus.CALCULATED;
    }

    /** Straightforward arithmetic on values believed current. */
    public ValueStatus forCalculation(Object value) {
        return value == null ? ValueStatus.UNAVAILABLE : ValueStatus.CALCULATED;
    }

    /** Inferred rather than measured, or derived from non-specific inputs. */
    public ValueStatus forEstimate(Object value) {
        return value == null ? ValueStatus.UNAVAILABLE : ValueStatus.ESTIMATED;
    }

    public long staleAfterHours() {
        return staleAfterHours;
    }
}
