package com.priceintel.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Verifies the 6-digit code from the activation email. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyActivationCodeRequest {

    @NotBlank(message = "token is required")
    private String token;

    @NotBlank(message = "code is required")
    @Pattern(regexp = "\\d{6}", message = "Code must be 6 digits")
    private String code;
}
