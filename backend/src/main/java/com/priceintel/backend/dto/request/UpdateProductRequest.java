package com.priceintel.backend.dto.request;

import java.math.BigDecimal;
import java.util.List;

import com.priceintel.backend.constants.ProductCondition;
import com.priceintel.backend.constants.ProductStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload for updating a product. Scalar fields are a partial update (only
 * non-null values change). If a child collection is provided (non-null), it
 * REPLACES the existing collection; if omitted, that collection is left as-is.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProductRequest {

    @Size(max = 500)
    private String title;

    @Size(max = 150)
    private String brand;

    @Size(max = 2000)
    private String description;

    @Size(max = 150)
    private String category;

    private ProductStatus status;

    @PositiveOrZero(message = "Pack quantity must be zero or positive")
    private Integer packQuantity;

    /** NEW, USED or REFURBISHED. Omit to leave unchanged. */
    private ProductCondition condition;

    @PositiveOrZero(message = "Our price must be zero or positive")
    private BigDecimal ourPrice;

    @PositiveOrZero
    private BigDecimal weight;

    @PositiveOrZero
    private BigDecimal height;

    @PositiveOrZero
    private BigDecimal width;

    @PositiveOrZero
    private BigDecimal length;

    @Valid
    private List<ProductIdentifierRequest> identifiers;

    @Valid
    private List<ProductImageRequest> images;

    @Valid
    private List<ProductAttributeRequest> attributes;
}
