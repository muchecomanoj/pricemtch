package com.priceintel.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A 6-digit authenticator code — used to enable or disable 2FA. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TwoFactorCodeRequest {

    @NotBlank(message = "Code is required")
    @Pattern(regexp = "\\d{6}", message = "Code must be 6 digits")
    private String code;
}
