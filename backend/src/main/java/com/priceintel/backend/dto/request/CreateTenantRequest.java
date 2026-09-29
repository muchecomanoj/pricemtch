package com.priceintel.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload to create a tenant (client organization) together with its first
 * Tenant Administrator user (the Client login). Used by SUPER_ADMIN and by the
 * public landing-page registration flow.
 *
 * <p>Only the company name and email are strictly required. Anything else the
 * caller omits is derived:
 * <ul>
 *   <li>{@code companyCode} — generated from the company name</li>
 *   <li>{@code adminEmail} — defaults to the company email</li>
 *   <li>{@code adminFirstName/adminLastName} — derived from {@code contactPerson}</li>
 *   <li>{@code adminPassword} — a temporary password is generated and returned once</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTenantRequest {

    // ---- company ----
    @NotBlank(message = "Company name is required")
    @Size(max = 200)
    private String companyName;

    /** Optional — generated from the company name when omitted. */
    @Size(max = 50)
    private String companyCode;

    @NotBlank(message = "Company email is required")
    @Email(message = "Company email must be valid")
    private String companyEmail;

    @Pattern(regexp = "^$|^[+]?[0-9\\-\\s()]{7,20}$", message = "Company phone must be valid")
    private String companyPhone;

    private String companyAddress;
    private String city;
    private String state;
    private String country;
    private String postalCode;
    private String timezone;
    private String currency;
    private String website;

    /**
     * Logo. Accepted as a URL string; any other JSON shape (e.g. an empty
     * object from a file input) is tolerated and ignored.
     */
    private Object logo;

    /** Main point of contact — also used to name the first admin user. */
    private String contactPerson;

    private String industry;

    /** Subscription plan code (e.g. FREE, BASIC, PRO, PROFESSIONAL, ENTERPRISE). */
    private String subscriptionPlan;

    /** Billing cycle the super admin chose (MONTHLY or YEARLY) — pre-selected for the client. */
    private com.priceintel.backend.constants.BillingCycle billingCycle;

    /** Trial length when no plan is assigned. Defaults to 14. */
    private Integer trialDays;

    /** Optional override of the plan's user limit. */
    private Integer maxUsers;

    // ---- first admin (the Client) ----
    private String adminFirstName;

    private String adminLastName;

    /** Optional — defaults to the company email. */
    @Email(message = "Admin email must be valid")
    private String adminEmail;

    /** Optional — a temporary password is generated when omitted. */
    private String adminPassword;
}
