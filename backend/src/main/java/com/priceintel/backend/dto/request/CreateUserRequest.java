package com.priceintel.backend.dto.request;

import com.priceintel.backend.constants.AccessType;
import com.priceintel.backend.validation.ValidPassword;

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
 * Payload for a Client (tenant admin) creating a user in their organization.
 * The tenant is taken from the caller's context — NOT from this request.
 * An access type is assigned (no roles).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {

    @NotBlank(message = "First name is required")
    @Size(max = 100)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100)
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 150)
    private String email;

    @Pattern(regexp = "^$|^[+]?[0-9\\-\\s()]{7,20}$", message = "Phone must be a valid number")
    private String phone;

    @NotBlank(message = "Password is required")
    @ValidPassword
    private String password;

    @NotNull(message = "Access type is required")
    private AccessType accessType;
}
