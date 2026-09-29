package com.priceintel.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductAttributeResponse {

    private Long id;
    private String color;
    private String size;
    private String material;
    private String model;
    private String variant;
    private String country;
    private String category;
}
