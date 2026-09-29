package com.priceintel.backend.dto.request;

import java.math.BigDecimal;

import com.priceintel.backend.constants.AlertCondition;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Create an alert rule for a product (the "Track price" button). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRuleRequest {

    @NotNull(message = "productId is required")
    private Long productId;

    @NotNull(message = "condition is required")
    private AlertCondition condition;

    /** "ABSOLUTE" or "PERCENT" (default PERCENT). */
    private String thresholdType;

    private BigDecimal thresholdValue;

    private String note;

    /** Delivery channels: IN_APP, EMAIL, SLACK, TEAMS. Empty = in-app only. */
    private java.util.List<String> channels;
}
