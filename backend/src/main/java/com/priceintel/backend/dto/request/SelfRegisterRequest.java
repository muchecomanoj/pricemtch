package com.priceintel.backend.dto.request;

import com.priceintel.backend.constants.BillingCycle;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Landing-page self-signup: the client's chosen plan + profile form. Submitting
 * this emails a verification code (no tenant is created yet).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SelfRegisterRequest {

    // ---- chosen on the landing page ----
    @NotBlank(message = "planCode is required")
    private String planCode;

    @NotNull(message = "billingCycle is required (MONTHLY or YEARLY)")
    private BillingCycle billingCycle;

    // ---- profile form ----
    @NotBlank(message = "Company name is required")
    @Size(max = 200)
    private String companyName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    private String contactPerson;

    @Pattern(regexp = "^$|^[+]?[0-9\\-\\s()]{7,20}$", message = "Phone must be valid")
    private String companyPhone;

    private String companyAddress;
    private String city;
    private String state;
    private String country;
    private String postalCode;
    private String timezone;
    private String currency;
    private String website;
    private String industry;
    private String gstin;
    private String taxId;
}
