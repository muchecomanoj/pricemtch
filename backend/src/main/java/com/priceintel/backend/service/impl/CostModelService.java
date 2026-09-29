package com.priceintel.backend.service.impl;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.priceintel.backend.dto.request.CostModelRequest;
import com.priceintel.backend.dto.response.CostModelResponse;
import com.priceintel.backend.entity.TenantCostModel;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.repository.TenantCostModelRepository;
import com.priceintel.backend.security.TenantContext;

import lombok.RequiredArgsConstructor;

/**
 * The tenant's default cost model (Cost Management page). One row per tenant.
 * Mirrors CostProfile so it can be the fallback when a product has no profile.
 */
@Service
@RequiredArgsConstructor
public class CostModelService {

    private final TenantCostModelRepository repo;

    @Transactional(readOnly = true)
    public CostModelResponse get() {
        Long tenantId = requireTenant();
        return repo.findByTenantId(tenantId)
                .map(this::toResponse)
                .orElseGet(() -> CostModelResponse.builder()
                        .tenantId(tenantId).currency("USD").configured(false).build());
    }

    /** Raw entity for a tenant, used by the profitability fallback. */
    @Transactional(readOnly = true)
    public Optional<TenantCostModel> findForTenant(Long tenantId) {
        return tenantId == null ? Optional.empty() : repo.findByTenantId(tenantId);
    }

    @Transactional
    public CostModelResponse save(CostModelRequest r) {
        Long tenantId = requireTenant();
        TenantCostModel m = repo.findByTenantId(tenantId)
                .orElseGet(() -> TenantCostModel.builder().tenantId(tenantId).build());
        if (r.getCurrency() != null && !r.getCurrency().isBlank()) {
            m.setCurrency(r.getCurrency());
        }
        m.setCogs(nz(r.getCogs()));
        m.setInboundFreight(nz(r.getInboundFreight()));
        m.setDuty(nz(r.getDuty()));
        m.setPrep(nz(r.getPrep()));
        m.setFulfilmentFee(nz(r.getFulfilmentFee()));
        m.setStorage(nz(r.getStorage()));
        m.setAdvertising(nz(r.getAdvertising()));
        m.setOverheadAllocation(nz(r.getOverheadAllocation()));
        m.setOutboundShipping(nz(r.getOutboundShipping()));
        m.setReferralRatePct(nz(r.getReferralRatePct()));
        m.setPaymentFeeRatePct(nz(r.getPaymentFeeRatePct()));
        m.setReturnsAllowancePct(nz(r.getReturnsAllowancePct()));
        m.setTaxRatePct(nz(r.getTaxRatePct()));
        if (r.getTaxTreatment() != null) {
            m.setTaxTreatment(r.getTaxTreatment());
        }
        return toResponse(repo.save(m));
    }

    private Long requireTenant() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BadRequestException("No tenant in context; cost model is per-tenant.");
        }
        return tenantId;
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private CostModelResponse toResponse(TenantCostModel m) {
        return CostModelResponse.builder()
                .tenantId(m.getTenantId()).currency(m.getCurrency())
                .cogs(m.getCogs()).inboundFreight(m.getInboundFreight()).duty(m.getDuty())
                .prep(m.getPrep()).fulfilmentFee(m.getFulfilmentFee()).storage(m.getStorage())
                .advertising(m.getAdvertising()).overheadAllocation(m.getOverheadAllocation())
                .outboundShipping(m.getOutboundShipping())
                .referralRatePct(m.getReferralRatePct()).paymentFeeRatePct(m.getPaymentFeeRatePct())
                .returnsAllowancePct(m.getReturnsAllowancePct()).taxRatePct(m.getTaxRatePct())
                .taxTreatment(m.getTaxTreatment() == null ? null : m.getTaxTreatment().name())
                .configured(true)
                .build();
    }
}
