package com.priceintel.backend.mapper;

import org.mapstruct.Mapper;

import com.priceintel.backend.dto.response.PlanResponse;
import com.priceintel.backend.dto.response.SubscriptionHistoryResponse;
import com.priceintel.backend.entity.SubscriptionHistory;
import com.priceintel.backend.entity.SubscriptionPlan;

/**
 * MapStruct mapper for subscription plans and history. The plan mapping is a
 * default method so it can expose the computed effective yearly price and the
 * effective discount %.
 */
@Mapper(componentModel = "spring")
public interface SubscriptionMapper {

    default PlanResponse toResponse(SubscriptionPlan plan) {
        if (plan == null) {
            return null;
        }
        return PlanResponse.builder()
                .id(plan.getId())
                .code(plan.getCode())
                .name(plan.getName())
                .description(plan.getDescription())
                .price(plan.getPrice())
                .yearlyPrice(plan.computeYearlyPrice())
                .yearlyDiscountPercent(plan.effectiveYearlyDiscountPercent())
                .currency(plan.getCurrency())
                .billingCycleDays(plan.getBillingCycleDays())
                .trialDays(plan.getTrialDays())
                .maxUsers(plan.getMaxUsers())
                .features(plan.getFeatures())
                .active(plan.isActive())
                .build();
    }

    SubscriptionHistoryResponse toResponse(SubscriptionHistory history);
}
