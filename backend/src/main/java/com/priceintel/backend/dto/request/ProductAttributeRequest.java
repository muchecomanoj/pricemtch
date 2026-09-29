package com.priceintel.backend.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductAttributeRequest {

    @Size(max = 100)
    private String color;

    @Size(max = 100)
    private String size;

    @Size(max = 150)
    private String material;

    @Size(max = 150)
    private String model;

    @Size(max = 150)
    private String variant;

    @Size(max = 100)
    private String country;

    @Size(max = 150)
    private String category;
}
