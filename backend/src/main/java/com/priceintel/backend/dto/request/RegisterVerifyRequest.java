package com.priceintel.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Verify the code emailed during self-registration. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterVerifyRequest {

    @NotBlank(message = "token is required")
    private String token;

    @NotBlank(message = "code is required")
    @Pattern(regexp = "\\d{6}", message = "Code must be 6 digits")
    private String code;
}
