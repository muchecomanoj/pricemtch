package com.priceintel.backend.dto.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** What the Account Verification page needs after the link is opened. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivationValidationResponse {
    private boolean valid;
    private String email;
    private String companyName;
    private String companyCode;
    private String contactPerson;
    private Instant expiresAt;
    // The plan/cycle the super admin picked when creating the client, so the
    // onboarding wizard can pre-select it instead of defaulting to "recommended".
    // The client may still change it before paying.
    private String assignedPlan;
    private String assignedBillingCycle;
}
