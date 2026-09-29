package com.priceintel.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Success-page payload: the Stripe Checkout Session id to confirm. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmPaymentRequest {

    @NotBlank(message = "sessionId is required")
    private String sessionId;
}
