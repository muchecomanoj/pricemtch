package com.priceintel.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Save edits to an email template. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailTemplateUpdateRequest {

    @NotBlank(message = "Subject is required")
    @Size(max = 300)
    private String subject;

    @NotBlank(message = "Body is required")
    @Size(max = 8000)
    private String body;
}
