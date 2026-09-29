package com.priceintel.backend.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.priceintel.backend.constants.ProductCondition;
import com.priceintel.backend.constants.ProductStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Full view of a product returned to clients, including its child collections.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {

    private Long id;
    private String sku;
    private String title;
    private String brand;
    private String description;
    private String category;
    private ProductStatus status;
    private Integer packQuantity;
    private ProductCondition condition;
    private BigDecimal ourPrice;
    private BigDecimal weight;
    private BigDecimal height;
    private BigDecimal width;
    private BigDecimal length;

    private List<ProductIdentifierResponse> identifiers;
    private List<ProductImageResponse> images;
    private List<ProductAttributeResponse> attributes;

    /**
     * Competitor listings attached to this product, rejected ones excluded.
     *
     * <p>This is what the Competitors tab lists, so the two always agree.
     * Unreviewed candidates are included, because they are shown there and do
     * influence the statistics.</p>
     */
    private Long competitorCount;

    /**
     * How many of those actually carry a price.
     *
     * <p>Separate from {@link #competitorCount} because the two genuinely differ:
     * a listing can be found on a marketplace and never successfully priced. Only
     * these feed the market statistics, the recommendation engine and the alert
     * engine, so a product can show twenty competitors and still have nothing to
     * compare against.</p>
     *
     * <p>Zero here means no price comparison is possible yet, whatever
     * {@code competitorCount} says — and it is the number to check before
     * telling a user their alert will work.</p>
     */
    private Long pricedCompetitorCount;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
}
