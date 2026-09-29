package com.priceintel.backend.dto.response;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The payment summary + how to proceed.
 * {@code mode} is STRIPE (open {@code checkoutUrl}) or MOCK (no gateway
 * configured — call the mock-confirm endpoint to simulate success).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutResponse {

    private String mode;          // STRIPE | MOCK
    private String checkoutUrl;   // Stripe-hosted page (STRIPE mode)
    private String sessionId;

    private String planCode;
    private String billingCycle;
    private BigDecimal amount;
    private String currency;
    private int trialDays;
    private String note;
}
