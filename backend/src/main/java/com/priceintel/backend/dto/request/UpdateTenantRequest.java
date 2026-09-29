package com.priceintel.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Partial update of a tenant's company profile. Company code and subscription
 * are managed via dedicated operations, not here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTenantRequest {

    @Size(max = 200)
    private String companyName;

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
    private String logo;
}
