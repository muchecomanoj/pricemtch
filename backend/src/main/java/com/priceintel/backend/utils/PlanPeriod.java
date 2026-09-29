package com.priceintel.backend.utils;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.priceintel.backend.constants.BillingCycle;

/**
 * How long a new company's access runs.
 *
 * <p>For a paid plan it is the trial plus the billing period it has paid for.
 * For a free plan the trial <em>is</em> the whole period: adding a billing
 * cycle on top turned "free for 10 days" into 10 days plus a month, and since
 * a zero-price renewal always succeeded, a free account could go on for
 * ever.</p>
 */
public final class PlanPeriod {

    private PlanPeriod() {
    }

    public static boolean isFree(BigDecimal price) {
        return price == null || price.signum() <= 0;
    }

    /**
     * @param price     what this plan charges for {@code cycle}
     * @param trialEnd  the day the trial runs out
     * @param cycle     the billing period being paid for
     * @return the last day the company has access
     */
    public static LocalDate accessEnds(BigDecimal price, LocalDate trialEnd, BillingCycle cycle) {
        if (isFree(price)) {
            return trialEnd;
        }
        return trialEnd.plusDays(cycle.getDays());
    }
}
