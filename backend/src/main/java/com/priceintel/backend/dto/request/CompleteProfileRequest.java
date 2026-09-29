package com.priceintel.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Client fills in company/business details during onboarding (the "Complete
 * Registration" page). Identified by the activation token.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteProfileRequest {

    @NotBlank(message = "token is required")
    private String token;

    private String companyName;
    private String companyAddress;
    private String companyPhone;
    private String website;
    private String city;
    private String state;
    private String country;
    private String postalCode;
    private String timezone;
    private String currency;

    /** GST / tax registration number. */
    private String gstin;
    private String taxId;
}
