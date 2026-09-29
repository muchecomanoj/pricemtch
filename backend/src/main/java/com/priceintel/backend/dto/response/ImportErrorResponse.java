package com.priceintel.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportErrorResponse {

    private Long id;
    private int rowNumber;
    private String sku;
    private String field;
    private String errorMessage;
    private String rawData;
}
