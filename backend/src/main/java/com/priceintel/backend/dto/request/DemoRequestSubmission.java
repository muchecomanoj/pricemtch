package com.priceintel.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** "Book a demo" on the landing page. Filled in by someone with no account. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemoRequestSubmission {

    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name is too long")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    @Size(max = 150, message = "Email is too long")
    private String email;

    @Size(max = 200, message = "Company name is too long")
    private String companyName;

    @Size(max = 30, message = "Phone number is too long")
    private String phone;

    @Size(max = 1000, message = "Message is too long — keep it under 1000 characters")
    private String message;
}
