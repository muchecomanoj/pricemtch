package com.priceintel.backend.dto.request;

import java.math.BigDecimal;
import java.util.List;

import com.priceintel.backend.constants.ProductCondition;
import com.priceintel.backend.constants.ProductStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload for creating a product, including its child identifiers, images, and
 * attributes in one request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProductRequest {

    @NotBlank(message = "SKU is required")
    @Size(max = 100)
    private String sku;

    @NotBlank(message = "Title is required")
    @Size(max = 500)
    private String title;

    @Size(max = 150)
    private String brand;

    @Size(max = 2000)
    private String description;

    /** Category name; created automatically if it does not exist yet. */
    @Size(max = 150)
    private String category;

    /** Optional; defaults to ACTIVE. */
    private ProductStatus status;

    @PositiveOrZero(message = "Pack quantity must be zero or positive")
    private Integer packQuantity;

    /** NEW, USED or REFURBISHED. Defaults to NEW when omitted. */
    private ProductCondition condition;

    /** Our current selling price (optional; profitability/recommendation anchor). */
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
