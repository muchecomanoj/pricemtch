package com.priceintel.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Simulates a successful payment for the onboarding identified by this token. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockPaymentRequest {

    @NotBlank(message = "token is required")
    private String token;
}
