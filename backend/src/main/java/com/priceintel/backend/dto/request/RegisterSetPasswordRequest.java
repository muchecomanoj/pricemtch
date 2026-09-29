package com.priceintel.backend.dto.request;

import com.priceintel.backend.validation.ValidPassword;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Set the password during self-registration. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterSetPasswordRequest {

    @NotBlank(message = "token is required")
    private String token;

    @NotBlank(message = "code is required")
    @Pattern(regexp = "\\d{6}", message = "Code must be 6 digits")
    private String code;

    @NotBlank(message = "password is required")
    @ValidPassword
    private String password;
}
