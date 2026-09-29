package com.priceintel.backend.dto.request;

import com.priceintel.backend.constants.IdentifierType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductIdentifierRequest {

    @NotNull(message = "Identifier type is required")
    private IdentifierType type;

    @NotBlank(message = "Identifier value is required")
    @Size(max = 200)
    private String originalValue;

    @Size(max = 100)
    private String marketplace;
}
