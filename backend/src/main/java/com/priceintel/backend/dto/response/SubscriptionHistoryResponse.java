package com.priceintel.backend.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.priceintel.backend.constants.SubscriptionAction;
import com.priceintel.backend.constants.SubscriptionStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionHistoryResponse {
    private Long id;
    private Long tenantId;
    private SubscriptionAction action;
    private String planCode;
    private SubscriptionStatus status;
    private LocalDate startDate;
    private LocalDate endDate;
    private String note;
    private LocalDateTime createdAt;
    private String createdBy;
}
