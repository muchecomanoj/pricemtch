package com.priceintel.backend.dto.response;

import com.priceintel.backend.constants.IdentifierType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductIdentifierResponse {

    private Long id;
    private IdentifierType type;
    private String originalValue;
    private String normalizedValue;
    private String marketplace;
}
