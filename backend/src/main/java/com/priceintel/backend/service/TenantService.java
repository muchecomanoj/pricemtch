package com.priceintel.backend.service;

import com.priceintel.backend.dto.request.CreateTenantRequest;
import com.priceintel.backend.dto.request.UpdateTenantRequest;
import com.priceintel.backend.dto.response.PagedResponse;
import com.priceintel.backend.dto.response.TenantResponse;
import com.priceintel.backend.dto.response.TenantStatsResponse;

/**
 * Tenant (client) management — SUPER_ADMIN operations, plus tenant creation used
 * by the public registration flow.
 */
public interface TenantService {

    TenantResponse createTenant(CreateTenantRequest request);

    TenantResponse getTenant(Long id);

    PagedResponse<TenantResponse> listTenants(String keyword, int page, int size, String sortBy, String direction);

    TenantResponse updateTenant(Long id, UpdateTenantRequest request);

    TenantResponse activate(Long id);

    TenantResponse deactivate(Long id);

    TenantResponse suspend(Long id);

    TenantResponse resume(Long id);

    void softDelete(Long id);

    TenantStatsResponse getStats(Long id);
}
