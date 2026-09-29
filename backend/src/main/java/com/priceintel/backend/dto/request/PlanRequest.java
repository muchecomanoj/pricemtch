package com.priceintel.backend.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Create/update a subscription plan (SUPER_ADMIN). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanRequest {

    @NotBlank(message = "Plan code is required")
    @Size(max = 50)
    private String code;

    @NotBlank(message = "Plan name is required")
    private String name;

    private String description;

    @PositiveOrZero(message = "Price must be zero or positive")
    private BigDecimal price;

    private String currency;

    @Positive(message = "Billing cycle days must be positive")
    private Integer billingCycleDays;

    @Positive(message = "Max users must be positive")
    private Integer maxUsers;

    @Positive(message = "Trial days must be positive")
    private Integer trialDays;

    /** Optional explicit yearly price; when set it overrides the discount calc. */
    @PositiveOrZero(message = "Yearly price must be zero or positive")
    private BigDecimal yearlyPrice;

    /** Yearly discount % (0–100) applied when no explicit yearly price is set. */
    @jakarta.validation.constraints.Min(value = 0, message = "Discount cannot be negative")
    @jakarta.validation.constraints.Max(value = 100, message = "Discount cannot exceed 100")
    private Integer yearlyDiscountPercent;

    private String features;

    private Boolean active;
}
