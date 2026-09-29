package com.priceintel.backend.dto.response;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Returned at each onboarding step and once the account is fully live. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivationCompleteResponse {

    /** True only once payment is complete and the account is ACTIVE. */
    private boolean activated;

    /** What the frontend should do next: COMPLETE_PROFILE, CHOOSE_PLAN, PAYMENT, DONE. */
    private String nextStep;

    private String email;
    private String companyName;
    private String companyCode;
    private String subscriptionPlan;
    private String billingCycle;
    private String subscriptionStatus;
    private LocalDate subscriptionStartDate;
    private LocalDate trialEndDate;
    private LocalDate subscriptionEndDate;
    private String loginUrl;
}
