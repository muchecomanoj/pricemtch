package com.priceintel.backend.service.impl;

import java.math.BigDecimal;
import com.priceintel.backend.constants.TaxTreatment;
import com.priceintel.backend.constants.ValueStatus;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.priceintel.backend.dto.request.CostProfileRequest;
import com.priceintel.backend.dto.response.CostProfileResponse;
import com.priceintel.backend.dto.response.ProfitabilityResponse;
import com.priceintel.backend.entity.CostProfile;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.exception.ResourceNotFoundException;
import com.priceintel.backend.repository.CostProfileRepository;
import com.priceintel.backend.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

/**
 * Cost-profile management (FR-COST-001) and the deterministic profitability
 * engine (FR-PROFIT-001). Tax is treated as pass-through (collected on behalf)
 * and excluded from profit; it's returned separately for information.
 */
@Service
@RequiredArgsConstructor
public class CostProfileService {

    private final CostProfileRepository costRepo;
    private final ProductRepository productRepo;
    private final CostModelService costModelService;

    /**
     * v2 added the tax treatment: net revenue is the listed price less the tax
     * when the price includes it. Pass-through products compute identically to
     * v1, but a stored figure should still say which engine produced it.
     */
    private static final String FORMULA_VERSION = "profit-v2";
    private static final int SCALE = 2;

    // ---------- cost profile ----------

    /** The costs in force today. */
    @Transactional(readOnly = true)
    public CostProfileResponse getCostProfile(Long productId) {
        return getCostProfile(productId, java.time.LocalDate.now());
    }

    /**
     * The costs in force on a date.
     *
     * <p>Asking "what did this cost us in June" is the whole point of dating
     * them: a margin recomputed with today's freight is not June's margin.</p>
     */
    @Transactional(readOnly = true)
    public CostProfileResponse getCostProfile(Long productId, java.time.LocalDate on) {
        requireProduct(productId);
        CostProfile effective = effectiveProfile(productId, on);
        return effective != null ? toResponse(effective)
                : CostProfileResponse.builder()
                        .productId(productId).currency("USD").configured(false).build();
    }

    /** Every version of a product's costs, newest effective date first. */
    @Transactional(readOnly = true)
    public java.util.List<CostProfileResponse> costHistory(Long productId) {
        requireProduct(productId);
        return costRepo.findByProductIdOrderByValidFromDesc(productId).stream()
                .map(this::toResponse).toList();
    }

    /** The profile that had already taken effect on this date, or null. */
    private CostProfile effectiveProfile(Long productId, java.time.LocalDate on) {
        java.util.List<CostProfile> found = costRepo.findEffectiveOn(
                productId, on == null ? java.time.LocalDate.now() : on,
                org.springframework.data.domain.PageRequest.of(0, 1));
        return found.isEmpty() ? null : found.get(0);
    }

    /**
     * Records costs effective from a date.
     *
     * <p>Saving against a date that already has a profile corrects that version;
     * a new date adds one and leaves the earlier costs intact. Correcting a typo
     * and recording a supplier increase are different acts, and the date is what
     * distinguishes them.</p>
     */
    @Transactional
    public CostProfileResponse saveCostProfile(Long productId, CostProfileRequest r) {
        requireProduct(productId);
        // No date given means "these are the costs now" — dating it today rather
        // than backdating to the beginning of time, which would rewrite history
        // nobody asked to change.
        java.time.LocalDate from = r.getValidFrom() != null
                ? r.getValidFrom() : java.time.LocalDate.now();

        CostProfile c = costRepo.findByProductIdAndValidFrom(productId, from)
                .orElseGet(() -> CostProfile.builder()
                        .productId(productId).validFrom(from).build());
        c.setValidFrom(from);
        if (r.getCurrency() != null) c.setCurrency(r.getCurrency());
        c.setCogs(nz(r.getCogs()));
        c.setInboundFreight(nz(r.getInboundFreight()));
        c.setDuty(nz(r.getDuty()));
        c.setPrep(nz(r.getPrep()));
        c.setFulfilmentFee(nz(r.getFulfilmentFee()));
        c.setStorage(nz(r.getStorage()));
        c.setAdvertising(nz(r.getAdvertising()));
        c.setOverheadAllocation(nz(r.getOverheadAllocation()));
        c.setOutboundShipping(nz(r.getOutboundShipping()));
        c.setReferralRatePct(nz(r.getReferralRatePct()));
        c.setPaymentFeeRatePct(nz(r.getPaymentFeeRatePct()));
        c.setReturnsAllowancePct(nz(r.getReturnsAllowancePct()));
        c.setTaxRatePct(nz(r.getTaxRatePct()));
        // Left alone when omitted: a caller sending only cost figures should not
        // silently move a VAT product onto US tax rules.
        if (r.getTaxTreatment() != null) {
            c.setTaxTreatment(r.getTaxTreatment());
        }
        c.setNote(r.getNote());
        return toResponse(costRepo.save(c));
    }

    // ---------- profitability ----------

    @Transactional(readOnly = true)
    public ProfitabilityResponse computeProfitability(Long productId, BigDecimal price) {
        return computeProfitability(productId, price, java.time.LocalDate.now());
    }

    /**
     * Profitability using the costs in force on a given date.
     *
     * <p>The date selects the cost profile, not the price. Recomputing a past
     * figure at today's costs produces a number that was never true and changes
     * again the next time a cost is edited.</p>
     */
    @Transactional(readOnly = true)
    public ProfitabilityResponse computeProfitability(
            Long productId, BigDecimal price, java.time.LocalDate on) {
        com.priceintel.backend.entity.Product product = productRepo.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        // Default to the product's own selling price (the anchor) when none is passed.
        if (price == null) {
            price = product.getOurPrice();
        }
        if (price == null || price.signum() <= 0) {
            throw new BadRequestException(
                    "Provide a price, or set the product's ourPrice, to compute profitability");
        }
        // Prefer the product's own cost profile; fall back to the tenant default
        // cost model when the product has none (FR-COST-001 cascade).
        CostProfile c = effectiveProfile(productId, on);
        String costSource = "PRODUCT";
        if (c == null) {
            c = costModelService.findForTenant(com.priceintel.backend.security.TenantContext.getTenantId())
                    .map(this::fromTenantModel).orElse(null);
            costSource = "TENANT_DEFAULT";
        }
        if (c == null) {
            // Nothing to compute from. Every figure is explicitly UNAVAILABLE so
            // the UI prompts for costs rather than rendering a row of zeroes.
            return ProfitabilityResponse.builder()
                    .productId(productId).sellingPrice(scale(price))
                    .costProfileConfigured(false).formulaVersion(FORMULA_VERSION)
                    .valueStatus(profitStatuses(null, null, null, null, null, null))
                    .build();
        }
        final String source = costSource;

        // Marketplace and payment fees are charged on the price the buyer pays,
        // which is the listed price under either tax treatment.
        BigDecimal referralFee = pct(price, c.getReferralRatePct());
        BigDecimal paymentFee = pct(price, c.getPaymentFeeRatePct());
        BigDecimal returnsAllowance = pct(price, c.getReturnsAllowancePct());

        boolean taxInclusive = c.getTaxTreatment() == TaxTreatment.INCLUSIVE
                && c.getTaxRatePct() != null && c.getTaxRatePct().signum() > 0;
        // Under an inclusive treatment the tax is a share of the listed price
        // and is remitted, not earned. Under pass-through it is added at
        // checkout and never was the seller's.
        BigDecimal tax = c.getTaxTreatment() == TaxTreatment.NONE ? BigDecimal.ZERO
                : taxInclusive ? price.subtract(netOfTax(price, c.getTaxRatePct()))
                : pct(price, c.getTaxRatePct());

        // Flat per-unit variable costs (COGS included; overhead is separate).
        BigDecimal flatCosts = c.getCogs().add(c.getInboundFreight()).add(c.getDuty())
                .add(c.getPrep()).add(c.getFulfilmentFee()).add(c.getStorage())
                .add(c.getAdvertising()).add(c.getOutboundShipping());
        BigDecimal variableCost = flatCosts.add(referralFee).add(paymentFee).add(returnsAllowance);

        // What the sale actually earns. Identical to the listed price when tax is
        // added at checkout; the price less the tax when it is already inside.
        BigDecimal netRevenue = taxInclusive ? netOfTax(price, c.getTaxRatePct()) : price;
        BigDecimal contributionProfit = netRevenue.subtract(variableCost);
        BigDecimal grossProfit = netRevenue.subtract(c.getCogs());
        BigDecimal netProfit = contributionProfit.subtract(c.getOverheadAllocation());

        BigDecimal marginPct = netRevenue.signum() == 0 ? null
                : contributionProfit.multiply(BigDecimal.valueOf(100))
                    .divide(netRevenue, SCALE, RoundingMode.HALF_UP);

        BigDecimal invested = c.getCogs().add(c.getInboundFreight()).add(c.getDuty());
        BigDecimal roiPct = invested.signum() == 0 ? null
                : contributionProfit.multiply(BigDecimal.valueOf(100))
                    .divide(invested, SCALE, RoundingMode.HALF_UP);

        // Break-even. Contribution is zero when
        //     price*taxShare − price*rateSum − flatCosts = 0
        // where taxShare is 1 under pass-through and 1/(1+t) when the price
        // already contains the tax — the seller only ever keeps that share.
        //     price = flatCosts / (taxShare − rateSum)
        BigDecimal rateSum = c.getReferralRatePct().add(c.getPaymentFeeRatePct())
                .add(c.getReturnsAllowancePct()).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        BigDecimal taxShare = taxInclusive
                ? BigDecimal.ONE.divide(taxMultiplier(c.getTaxRatePct()), 6, RoundingMode.HALF_UP)
                : BigDecimal.ONE;
        BigDecimal denom = taxShare.subtract(rateSum);
        BigDecimal breakEven = denom.signum() <= 0 ? null
                : flatCosts.divide(denom, SCALE, RoundingMode.HALF_UP);

        // Max COGS that still breaks even at this price — from net revenue, not
        // the listed price, or an inclusive-tax product would appear to afford
        // stock it cannot.
        BigDecimal nonCogsFlat = flatCosts.subtract(c.getCogs());
        BigDecimal maxCogs = netRevenue.subtract(nonCogsFlat).subtract(referralFee)
                .subtract(paymentFee).subtract(returnsAllowance);

        return ProfitabilityResponse.builder()
                .productId(productId).currency(c.getCurrency()).costProfileConfigured(true)
                .costSource(source)
                .sellingPrice(scale(price))
                .referralFee(scale(referralFee)).paymentFee(scale(paymentFee))
                .returnsAllowance(scale(returnsAllowance)).tax(scale(tax))
                .taxTreatment(c.getTaxTreatment() == null ? null : c.getTaxTreatment().name())
                .variableCost(scale(variableCost)).netRevenue(scale(netRevenue))
                .contributionProfit(scale(contributionProfit)).grossProfit(scale(grossProfit))
                .netProfitEstimate(scale(netProfit))
                .contributionMarginPct(marginPct).roiPct(roiPct)
                .breakEvenPrice(breakEven == null ? null : scale(breakEven))
                .maxAllowableAcquisitionCost(scale(maxCogs))
                .formulaVersion(FORMULA_VERSION)
                .valueStatus(profitStatuses(source, netProfit, marginPct, roiPct, breakEven, maxCogs))
                .build();
    }

    // ---------- helpers ----------

    private void requireProduct(Long productId) {
        if (!productRepo.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found: " + productId);
        }
    }

    /**
     * Labels each profit figure with how far it can be trusted (FR-REPORT-001).
     *
     * <p>The arithmetic is deterministic either way; what varies is how specific
     * the inputs are. Costs held against this product give a CALCULATED figure;
     * the tenant-wide default gives an ESTIMATED one, because the same generic
     * costs are being applied to every product. Where a figure could not be
     * produced at all — a break-even with a non-positive denominator, an ROI
     * with nothing invested — it is UNAVAILABLE rather than zero.</p>
     */
    private java.util.Map<String, String> profitStatuses(
            String costSource, BigDecimal netProfit, BigDecimal marginPct,
            BigDecimal roiPct, BigDecimal breakEven, BigDecimal maxCogs) {
        ValueStatus present = "PRODUCT".equals(costSource)
                ? ValueStatus.CALCULATED : ValueStatus.ESTIMATED;
        java.util.function.Function<BigDecimal, String> status =
                v -> (v == null ? ValueStatus.UNAVAILABLE : present).name();
        return java.util.Map.of(
                "netProfitEstimate", status.apply(netProfit),
                "contributionMarginPct", status.apply(marginPct),
                "roiPct", status.apply(roiPct),
                "breakEvenPrice", status.apply(breakEven),
                "maxAllowableAcquisitionCost", status.apply(maxCogs));
    }

    /** 1 + the tax rate, as a multiplier. */
    private BigDecimal taxMultiplier(BigDecimal taxRatePct) {
        return BigDecimal.ONE.add(
                nz(taxRatePct).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
    }

    /** The part of a tax-inclusive price the seller keeps. */
    private BigDecimal netOfTax(BigDecimal price, BigDecimal taxRatePct) {
        return price.divide(taxMultiplier(taxRatePct), SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal pct(BigDecimal price, BigDecimal ratePct) {
        return price.multiply(nz(ratePct)).divide(BigDecimal.valueOf(100), SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** Build a transient (unsaved) cost profile from the tenant default model. */
    private CostProfile fromTenantModel(com.priceintel.backend.entity.TenantCostModel m) {
        return CostProfile.builder()
                .currency(m.getCurrency())
                .cogs(m.getCogs()).inboundFreight(m.getInboundFreight()).duty(m.getDuty())
                .prep(m.getPrep()).fulfilmentFee(m.getFulfilmentFee()).storage(m.getStorage())
                .advertising(m.getAdvertising()).overheadAllocation(m.getOverheadAllocation())
                .outboundShipping(m.getOutboundShipping())
                .referralRatePct(m.getReferralRatePct()).paymentFeeRatePct(m.getPaymentFeeRatePct())
                .returnsAllowancePct(m.getReturnsAllowancePct()).taxRatePct(m.getTaxRatePct())
                .taxTreatment(m.getTaxTreatment())
                .build();
    }

    private BigDecimal scale(BigDecimal v) {
        return v == null ? null : v.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private CostProfileResponse toResponse(CostProfile c) {
        return CostProfileResponse.builder()
                .productId(c.getProductId()).currency(c.getCurrency())
                .cogs(c.getCogs()).inboundFreight(c.getInboundFreight()).duty(c.getDuty())
                .prep(c.getPrep()).fulfilmentFee(c.getFulfilmentFee()).storage(c.getStorage())
                .advertising(c.getAdvertising()).overheadAllocation(c.getOverheadAllocation())
                .outboundShipping(c.getOutboundShipping())
                .referralRatePct(c.getReferralRatePct()).paymentFeeRatePct(c.getPaymentFeeRatePct())
                .returnsAllowancePct(c.getReturnsAllowancePct()).taxRatePct(c.getTaxRatePct())
                .taxTreatment(c.getTaxTreatment() == null ? null : c.getTaxTreatment().name())
                .validFrom(c.getValidFrom()).note(c.getNote()).configured(true)
                .build();
    }
}
