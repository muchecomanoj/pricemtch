package com.priceintel.backend.dto.response;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One KPI tile on the Dashboard.
 *
 * <p>Shaped exactly as the screen already expects: a key, a label, a value, and
 * the icon and colour to draw it with.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DashboardSummaryItem {

    private String key;
    private String label;
    private BigDecimal value;

    /** Drawn before the value, e.g. "$". */
    private String prefix;
    /** Drawn after the value, e.g. "%". */
    private String suffix;

    /** One of: box, check, list, search, cpu, percent, dollar, trend. */
    private String icon;
    /** One of: primary, success, info, warning, danger. */
    private String color;

    /**
     * Percentage change against the previous period, or null when there is
     * nothing to compare with — a company in its first month has no "last
     * month", and inventing 0% there would read as "no change".
     */
    private Double delta;
}
