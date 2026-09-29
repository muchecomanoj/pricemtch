package com.priceintel.backend.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The normalized set of attributes that matching compares, built from either a
 * stored Product or an inline candidate.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComparableProduct {
    private String brand;
    private String title;
    private String model;
    private String color;
    private String variant;
    private Integer packQuantity;
    private String imageUrl;
}
