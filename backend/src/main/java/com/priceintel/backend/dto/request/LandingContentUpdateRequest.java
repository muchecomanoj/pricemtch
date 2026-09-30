package com.priceintel.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Super Admin edit of one landing-page section. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LandingContentUpdateRequest {

    /** The section's content as a JSON object. Rejected if it does not parse. */
    @NotBlank(message = "Content is required")
    private String payload;

    /** Null leaves the section's visibility as it is. */
    private Boolean active;
}
