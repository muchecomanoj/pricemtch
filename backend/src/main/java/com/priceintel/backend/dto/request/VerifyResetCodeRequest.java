package com.priceintel.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Step 2 of forgot-password: verify the 6-digit code from the email. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyResetCodeRequest {

    @NotBlank(message = "Please enter your email address.")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Verification code is required")
    @Pattern(regexp = "\\d{6}", message = "Code must be 6 digits")
    private String code;
}
