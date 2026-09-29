package com.priceintel.backend.dto.request;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtendSubscriptionRequest {

    @Positive(message = "days must be positive")
    private int days;
}
