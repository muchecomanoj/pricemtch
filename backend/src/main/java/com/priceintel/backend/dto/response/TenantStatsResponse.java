package com.priceintel.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Usage/statistics snapshot for a tenant (SUPER_ADMIN view). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantStatsResponse {
    private Long tenantId;
    private String companyName;
    private long totalUsers;
    private long activeUsers;
    private int maxUsers;
    private String subscriptionPlan;
    private String subscriptionStatus;
    private String tenantStatus;
}
