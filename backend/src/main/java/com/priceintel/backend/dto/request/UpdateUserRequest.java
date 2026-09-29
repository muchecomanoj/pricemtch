package com.priceintel.backend.dto.request;

import com.priceintel.backend.constants.AccessType;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Partial update of a tenant user. Email and tenant cannot be changed here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String lastName;

    @Pattern(regexp = "^$|^[+]?[0-9\\-\\s()]{7,20}$", message = "Phone must be a valid number")
    private String phone;

    private AccessType accessType;

    @Size(max = 1000)
    private String profileImage;
}
