package com.priceintel.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Send a test render of a template to an address. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendTestEmailRequest {

    @NotBlank(message = "Recipient email is required")
    @Email(message = "Recipient email must be valid")
    private String toEmail;
}
