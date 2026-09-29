package com.priceintel.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Validates the activation link token from the email. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidateActivationRequest {

    @NotBlank(message = "token is required")
    private String token;
}
