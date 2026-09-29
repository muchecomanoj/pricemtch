package com.priceintel.backend.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One row of the payment history shown to the super admin. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private Long id;
    private Long tenantId;
    private String planCode;
    private String billingCycle;
    private BigDecimal amount;
    private String currency;
    private String type;
    private String status;
    private String method;
    private String stripeSessionId;
    private Instant paidAt;
    private LocalDateTime createdAt;
    private String note;
}
