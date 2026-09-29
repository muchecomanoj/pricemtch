package com.priceintel.backend.dto.request;

import com.priceintel.backend.validation.ValidPassword;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Step 3 of forgot-password: set the new password. The code is re-checked here
 * so the reset cannot be performed without it.
 *
 * <p>Confirm-password matching is a frontend concern; the backend validates the
 * password policy.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequest {

    @NotBlank(message = "Please enter your email address.")
    @Email(message = "Email must be valid")
    private String email;

    @NotBlank(message = "Verification code is required")
    @Pattern(regexp = "\\d{6}", message = "Code must be 6 digits")
    private String code;

    @NotBlank(message = "New password is required")
    @ValidPassword
    private String newPassword;
}
