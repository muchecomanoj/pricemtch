package com.priceintel.backend.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.priceintel.backend.entity.PricingPolicy;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.exception.ResourceNotFoundException;
import com.priceintel.backend.repository.PricingPolicyRepository;
import com.priceintel.backend.security.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves and applies the bounds a recommended price must respect
 * (FR-REC-002).
 *
 * <p>The bounds live here rather than in the optimiser because they are a
 * business decision, not part of the calculation: the optimiser answers "what
 * price is best", the policy answers "what price are we willing to publish".
 * Keeping them apart means a capped recommendation can still show what it
 * would have been, which is the only way to tell a policy that is protecting
 * you from one that is quietly wrong.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingPolicyService {

    private final PricingPolicyRepository repo;

    /** Which bound moved a price, for the record and for the screen. */
    public enum Bound {
        NONE, FLOOR_PRICE, FLOOR_MARGIN, CEILING, MAX_CHANGE
    }

    /** A bounded price and what constrained it. */
    public record Bounded(BigDecimal price, BigDecimal unbounded, Bound bound) {

        public boolean wasCapped() {
            return bound != Bound.NONE;
        }
    }

    // ---------- resolution ----------

    /**
     * The policy governing this product on this channel, or null when none is
     * configured.
     *
     * <p>Most specific wins: a rule for one product on one channel beats a rule
     * for the product, which beats a company-wide rule. Ties cannot occur — the
     * unique index forbids two policies of the same scope.</p>
     */
    @Transactional(readOnly = true)
    public PricingPolicy resolve(Long productId, String marketplace) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return null;
        }
        return repo.findByTenantIdAndActiveTrue(tenantId).stream()
                .filter(p -> p.getProductId() == null || p.getProductId().equals(productId))
                .filter(p -> p.getMarketplace() == null
                        || p.getMarketplace().equalsIgnoreCase(marketplace))
                .max(Comparator.comparingInt(PricingPolicy::specificity))
                .orElse(null);
    }

    // ---------- application ----------

    /**
     * Brings a price inside its policy.
     *
     * <p>Order matters. The ceiling is applied before the floor so that a floor
     * above a ceiling — a misconfiguration — resolves upward to the floor rather
     * than downward to a price below cost. Selling too high loses a sale;
     * selling below cost loses money on every sale.</p>
     *
     * @param currentPrice what the product sells at now, for the change limit.
     *                     Null skips that check — there is no move to measure.
     * @param breakEven    the price at which this product breaks even, used for
     *                     the margin floor. Null skips it.
     */
    public Bounded apply(BigDecimal price, PricingPolicy policy,
                         BigDecimal currentPrice, BigDecimal breakEven) {
        if (price == null) {
            return new Bounded(null, null, Bound.NONE);
        }
        BigDecimal unbounded = price.setScale(2, RoundingMode.HALF_UP);
        if (policy == null) {
            return new Bounded(unbounded, unbounded, Bound.NONE);
        }

        BigDecimal result = unbounded;
        Bound bound = Bound.NONE;

        // How far one cycle may move. Checked first, so a step limit cannot be
        // escaped by a floor or ceiling pulling the price further than allowed.
        if (currentPrice != null && currentPrice.signum() > 0) {
            BigDecimal limit = changeLimit(policy, currentPrice);
            if (limit != null) {
                BigDecimal delta = result.subtract(currentPrice);
                if (delta.abs().compareTo(limit) > 0) {
                    result = delta.signum() > 0
                            ? currentPrice.add(limit) : currentPrice.subtract(limit);
                    bound = Bound.MAX_CHANGE;
                }
            }
        }

        if (policy.getCeilingPrice() != null && result.compareTo(policy.getCeilingPrice()) > 0) {
            result = policy.getCeilingPrice();
            bound = Bound.CEILING;
        }

        // Floors last, and the higher of the two wins: a fixed floor cannot know
        // today's costs, a margin floor cannot express "never below what we paid".
        BigDecimal marginFloor = marginFloor(policy, breakEven);
        if (marginFloor != null && result.compareTo(marginFloor) < 0) {
            result = marginFloor;
            bound = Bound.FLOOR_MARGIN;
        }
        if (policy.getFloorPrice() != null && result.compareTo(policy.getFloorPrice()) < 0) {
            result = policy.getFloorPrice();
            bound = Bound.FLOOR_PRICE;
        }

        return new Bounded(result.setScale(2, RoundingMode.HALF_UP), unbounded, bound);
    }

    /** The tighter of the percentage and absolute change limits, or null. */
    private BigDecimal changeLimit(PricingPolicy policy, BigDecimal currentPrice) {
        BigDecimal byPct = policy.getMaxChangePct() == null ? null
                : currentPrice.multiply(policy.getMaxChangePct())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal byAmount = policy.getMaxChangeAmount();
        if (byPct == null) {
            return byAmount;
        }
        if (byAmount == null) {
            return byPct;
        }
        return byPct.min(byAmount);
    }

    /** Break-even lifted by the required margin, or null when either is absent. */
    private BigDecimal marginFloor(PricingPolicy policy, BigDecimal breakEven) {
        if (policy.getFloorMarginPct() == null || breakEven == null) {
            return null;
        }
        return breakEven.multiply(BigDecimal.ONE.add(
                        policy.getFloorMarginPct().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)))
                .setScale(2, RoundingMode.HALF_UP);
    }

    // ---------- cooldown ----------

    /**
     * Whether enough time has passed since the last published change.
     *
     * @return null when publishing is allowed, otherwise why it is not
     */
    public String cooldownBlock(PricingPolicy policy, java.time.Instant lastPublishedAt) {
        if (policy == null || policy.getCooldownHours() == null || lastPublishedAt == null) {
            return null;
        }
        java.time.Instant allowedFrom =
                lastPublishedAt.plus(java.time.Duration.ofHours(policy.getCooldownHours()));
        if (java.time.Instant.now().isBefore(allowedFrom)) {
            long minutes = java.time.Duration.between(java.time.Instant.now(), allowedFrom).toMinutes();
            return "This product's price changed less than " + policy.getCooldownHours()
                    + " hour(s) ago. The cooldown has " + Math.max(1, minutes)
                    + " minute(s) left — it exists so the system does not react to its own "
                    + "previous move before the market has responded to it.";
        }
        return null;
    }

    // ---------- editing ----------

    @Transactional
    public PricingPolicy save(PricingPolicy policy) {
        policy.setTenantId(TenantContext.getTenantId());

        if (policy.getFloorPrice() != null && policy.getCeilingPrice() != null
                && policy.getFloorPrice().compareTo(policy.getCeilingPrice()) > 0) {
            throw new BadRequestException(
                    "The floor is above the ceiling, so no price could satisfy both.");
        }
        // Automation without bounds is the state this whole feature exists to
        // prevent, so it is refused rather than allowed and warned about.
        if (policy.isAutoPublish() && !policy.hasAnyBound()) {
            throw new BadRequestException(
                    "Automatic publishing needs at least one bound — a floor, a ceiling or a "
                            + "change limit. Without one there is nothing to stop a bad "
                            + "recommendation going live unseen.");
        }
        return repo.save(policy);
    }

    @Transactional(readOnly = true)
    public List<PricingPolicy> list() {
        return repo.findByTenantId(TenantContext.getTenantId());
    }

    @Transactional
    public void delete(Long id) {
        PricingPolicy p = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found: " + id));
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null && !tenantId.equals(p.getTenantId())) {
            throw new ResourceNotFoundException("Policy not found: " + id);
        }
        repo.delete(p);
    }
}
