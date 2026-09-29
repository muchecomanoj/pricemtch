package com.priceintel.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A question for the AI Analyst. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalystAskRequest {
    @NotBlank(message = "Question is required")
    @Size(max = 500)
    private String question;
}
