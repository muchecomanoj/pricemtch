package com.priceintel.backend.dto.request;

import com.priceintel.backend.constants.BillingCycle;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Client selects a plan and billing cycle during onboarding. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChoosePlanRequest {

    @NotBlank(message = "token is required")
    private String token;

    @NotBlank(message = "planCode is required")
    private String planCode;

    @NotNull(message = "billingCycle is required (MONTHLY or YEARLY)")
    private BillingCycle billingCycle;
}
