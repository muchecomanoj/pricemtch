package com.priceintel.backend.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Self-service profile update (Settings → Profile). Only the fields a user may
 * change about themselves. Email and role are managed by an administrator.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String lastName;

    @Pattern(regexp = "^$|^[+]?[0-9\\-\\s()]{7,20}$", message = "Phone must be valid")
    private String phone;

    /** URL or base64 data: URI (direct upload). */
    private String profileImage;
}
