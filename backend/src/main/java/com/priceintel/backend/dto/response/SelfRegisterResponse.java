package com.priceintel.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Progress of a self-registration. The {@code token} threads through every
 * subsequent step; {@code nextStep} tells the frontend which page to show.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SelfRegisterResponse {

    /** Held by the client through verify → set-password → payment. */
    private String token;
    private String email;
    private String status;
    private String nextStep; // VERIFY_CODE, SET_PASSWORD, PAYMENT, DONE
    private boolean completed;

    // populated on completion
    private String companyCode;
    private String subscriptionPlan;
    private String billingCycle;
    private String subscriptionStatus;
    private java.time.LocalDate trialEndDate;
    private java.time.LocalDate subscriptionEndDate;
    private String loginUrl;
}
