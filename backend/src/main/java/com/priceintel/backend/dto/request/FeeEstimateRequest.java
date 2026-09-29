package com.priceintel.backend.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeEstimateRequest {

    @NotBlank(message = "marketplaceItemId is required")
    @Size(max = 200)
    private String marketplaceItemId;

    @NotNull(message = "price is required")
    @PositiveOrZero(message = "price must be zero or positive")
    private BigDecimal price;

    @Size(max = 3)
    private String currency;
}
