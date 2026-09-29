package com.priceintel.backend.dto.request;

import com.priceintel.backend.validation.ValidPassword;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Final activation step: sets the client's password. The token and code are
 * re-checked here so the flow stays stateless and safe.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetPasswordRequest {

    @NotBlank(message = "token is required")
    private String token;

    @NotBlank(message = "code is required")
    @Pattern(regexp = "\\d{6}", message = "Code must be 6 digits")
    private String code;

    @NotBlank(message = "password is required")
    @ValidPassword
    private String password;
}
