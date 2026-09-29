package com.priceintel.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRuleResponse {
    private Long id;
    private Long productId;
    private String condition;
    private String thresholdType;
    private BigDecimal thresholdValue;
    private boolean active;
    private String note;

    /** Delivery channels for this rule. Empty means in-app only. */
    private java.util.List<String> channels;
    private java.time.Instant lastFiredAt;
    private int triggerCount;
    private LocalDateTime createdAt;
}
