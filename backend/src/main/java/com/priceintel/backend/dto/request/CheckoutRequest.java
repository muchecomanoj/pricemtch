package com.priceintel.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Starts the payment for the selected plan. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutRequest {

    @NotBlank(message = "token is required")
    private String token;

    /** Where Stripe redirects after success/cancel; frontend supplies these. */
    private String successUrl;
    private String cancelUrl;
}
