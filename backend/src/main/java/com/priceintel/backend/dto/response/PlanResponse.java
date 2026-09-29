package com.priceintel.backend.dto.response;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanResponse {
    private Long id;
    private String code;
    private String name;
    private String description;
    private BigDecimal price;                 // monthly price
    private BigDecimal yearlyPrice;           // effective yearly price (computed or explicit)
    private Integer yearlyDiscountPercent;    // discount applied for yearly billing
    private String currency;
    private int billingCycleDays;
    private int trialDays;
    private int maxUsers;
    private String features;
    private boolean active;
}
