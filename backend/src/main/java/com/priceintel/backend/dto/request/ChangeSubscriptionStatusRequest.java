package com.priceintel.backend.dto.request;

import com.priceintel.backend.constants.SubscriptionStatus;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeSubscriptionStatusRequest {

    @NotNull(message = "status is required")
    private SubscriptionStatus status;
}
