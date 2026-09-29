package com.priceintel.backend.marketplace.model;

import java.math.BigDecimal;

import com.priceintel.backend.marketplace.Marketplace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Estimated marketplace fees for a listing at a given price. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeEstimate {
    private Marketplace marketplace;
    private String marketplaceItemId;
    private BigDecimal price;
    private BigDecimal referralFee;
    private BigDecimal fulfilmentFee;
    private BigDecimal totalFees;
    private String currency;
    private boolean mocked;
}
