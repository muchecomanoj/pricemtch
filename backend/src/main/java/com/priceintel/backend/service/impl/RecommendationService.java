package com.priceintel.backend.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.priceintel.backend.constants.RecommendationStatus;
import com.priceintel.backend.dto.request.RecommendationRequest;
import com.priceintel.backend.dto.response.MarketPricesResponse;
import com.priceintel.backend.dto.response.ProfitabilityResponse;
import com.priceintel.backend.dto.response.RecommendationResponse;
import com.priceintel.backend.entity.PriceRecommendation;
import com.priceintel.backend.entity.PricingPolicy;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.exception.ResourceNotFoundException;
import com.priceintel.backend.repository.PriceRecommendationRepository;
import com.priceintel.backend.repository.ProductRepository;
import com.priceintel.backend.security.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Deterministic price-recommendation engine (FR-REC-001 / §10.2). Builds a
 * candidate price from competitor stats and the cost floor, validates margin
 * constraints, and explains the result. Approval is required before publish.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final ProductRepository productRepo;
    private final PriceRecommendationRepository repo;
    private final CostProfileService costService;
    private final SearchPipelineService searchService;
    private final PricingPolicyService policyService;

    private static final BigDecimal DEFAULT_TARGET_MARGIN = BigDecimal.valueOf(20);

    @Transactional
    public RecommendationResponse generate(Long productId, RecommendationRequest r) {
        if (!productRepo.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found: " + productId);
        }
        BigDecimal targetMargin = r != null && r.getTargetMarginPct() != null
                ? r.getTargetMarginPct() : DEFAULT_TARGET_MARGIN;

        MarketPricesResponse market = searchService.marketPrices(productId, false);
        BigDecimal competitorMedian = market.getMedian();

        // Break-even from the cost profile (price-independent). Null if no costs.
        BigDecimal probePrice = competitorMedian != null ? competitorMedian : BigDecimal.valueOf(100);
        ProfitabilityResponse probe = costService.computeProfitability(productId, probePrice);
        boolean hasCosts = probe.isCostProfileConfigured();
        BigDecimal breakEven = hasCosts ? probe.getBreakEvenPrice() : null;

        if (competitorMedian == null && !hasCosts) {
            throw new BadRequestException(
                    "Need competitor prices or a cost profile to generate a recommendation.");
        }

        // Candidate: align to competitor median, but never below the margin-target floor.
        BigDecimal marginFloor = breakEven == null ? null
                : breakEven.multiply(BigDecimal.ONE.add(targetMargin.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)))
                    .setScale(2, RoundingMode.HALF_UP);

        BigDecimal candidate;
        if (competitorMedian != null && marginFloor != null) {
            candidate = competitorMedian.max(marginFloor);
        } else if (competitorMedian != null) {
            candidate = competitorMedian;
        } else {
            candidate = marginFloor;
        }
        candidate = clamp(candidate, r);

        // The stored policy, applied before the margin is worked out. A capped
        // price has a different margin from the one the optimiser aimed at, and
        // reporting the pre-cap figure would defeat the margin floor that caused
        // the cap. Bounds supplied on the request still work as a one-off
        // override; the policy is what protects an unattended run.
        var policy = policyService.resolve(productId, null);
        var bounded = policyService.apply(candidate, policy,
                productRepo.findById(productId).map(p -> p.getOurPrice()).orElse(null),
                breakEven);
        if (bounded.wasCapped()) {
            log.info("Recommendation for product {} capped by {}: {} -> {}",
                    productId, bounded.bound(), bounded.unbounded(), bounded.price());
        }
        candidate = bounded.price();

        ProfitabilityResponse atCandidate = costService.computeProfitability(productId, candidate);
        BigDecimal expectedMargin = atCandidate.getContributionMarginPct();

        // Confirmed matches counted separately from unreviewed candidates.
        MarketPricesResponse confirmed = searchService.marketPrices(productId, true);
        BigDecimal confidence = confidence(market.getCompetitorCount(),
                confirmed.getCompetitorCount(), hasCosts);
        String rationale = rationale(candidate, competitorMedian, breakEven, expectedMargin,
                market.getCompetitorCount(), targetMargin);
        if (bounded.wasCapped()) {
            rationale = rationale + " Held at " + bounded.price() + " by the "
                    + describeBound(bounded.bound()) + " (unbounded: " + bounded.unbounded() + ").";
        }

        // Generating again replaces the pending draft rather than adding to it.
        // Asking for a recommendation means wanting the current answer, not a
        // second copy — and two identical drafts leave the approver choosing
        // between things that are the same. A double-click produced exactly
        // that: two drafts seven seconds apart, both $109.15.
        PriceRecommendation existing = repo
                .findFirstByProductIdAndStatusOrderByIdDesc(productId, RecommendationStatus.DRAFT)
                .orElse(null);
        if (existing != null) {
            log.info("Replacing pending draft {} for product {}", existing.getId(), productId);
            repo.delete(existing);
        }

        PriceRecommendation rec = repo.save(PriceRecommendation.builder()
                .productId(productId)
                .recommendedPrice(candidate)
                .unboundedPrice(bounded.unbounded())
                .boundApplied(bounded.wasCapped() ? bounded.bound().name() : null)
                .currency(market.getCurrency() != null ? market.getCurrency()
                        : (atCandidate.getCurrency() != null ? atCandidate.getCurrency() : "USD"))
                .expectedMarginPct(expectedMargin)
                .breakEvenPrice(breakEven)
                .competitorMedian(competitorMedian)
                .confidence(confidence)
                .rationale(rationale)
                .status(RecommendationStatus.DRAFT)
                .expiresAt(LocalDate.now().plusDays(7))
                .build());
        log.info("Recommendation {} for product {}: price {} (margin {}%, confidence {})",
                rec.getId(), productId, candidate, expectedMargin, confidence);

        if (policy != null && policy.isAutoPublish()) {
            return autoPublish(rec, policy);
        }
        return toResponse(rec);
    }

    /**
     * Takes a draft straight to live under an auto-publish policy (FR-REC-002).
     *
     * <p>Only reachable when a policy has bounds — {@code save()} refuses the
     * flag otherwise — so an unattended price is always a bounded one.</p>
     *
     * <p>The cooldown is tested here rather than letting {@code publish()} refuse,
     * because that refusal is an exception and would roll back the surrounding
     * transaction, discarding a correct recommendation over a price that merely
     * may not move <em>yet</em>. Blocked drafts are left to be published by hand.</p>
     */
    private RecommendationResponse autoPublish(PriceRecommendation rec, PricingPolicy policy) {
        String blocked = policyService.cooldownBlock(policy, lastPublishedAt(rec.getProductId()));
        if (blocked != null) {
            log.info("Auto-publish held for recommendation {}: {}", rec.getId(), blocked);
            return toResponse(rec);
        }
        approve(rec.getId());
        RecommendationResponse published = publish(rec.getId());
        log.info("Auto-published recommendation {} for product {} at {}",
                rec.getId(), rec.getProductId(), rec.getRecommendedPrice());
        return published;
    }

    @Transactional(readOnly = true)
    public List<RecommendationResponse> list(Long productId) {
        com.priceintel.backend.entity.Product product = tenantProductOrNull(productId);
        if (product == null) {
            // Either no such product, or it belongs to another client. Same
            // answer either way — an empty list rather than a hint.
            return List.of();
        }
        return repo.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .map(r -> toResponse(r, product)).toList();
    }

    /**
     * Tenant-wide recommendations for the Recommendations page (paginated),
     * optionally filtered by status. Enriched with product title, current
     * price, and a risk level.
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<RecommendationResponse> listAll(
            RecommendationStatus status, org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Page<PriceRecommendation> page = repo.findForList(status, TenantContext.scopeOrAllForSuperAdmin(), pageable);
        java.util.Set<Long> ids = page.getContent().stream()
                .map(PriceRecommendation::getProductId).collect(java.util.stream.Collectors.toSet());
        java.util.Map<Long, com.priceintel.backend.entity.Product> products = new java.util.HashMap<>();
        if (!ids.isEmpty()) {
            productRepo.findAllById(ids).forEach(p -> products.put(p.getId(), p));
        }
        return page.map(r -> toResponse(r, products.get(r.getProductId())));
    }

    /**
     * Generate a DRAFT recommendation for every product that has enough data
     * and doesn't already have a DRAFT. Used by the "Generate recommendations"
     * action on the Recommendations page. Products lacking data are skipped.
     */
    /** Products the caller owns; the platform owner sees every tenant's. */
    private List<com.priceintel.backend.entity.Product> visibleProducts() {
        Long tenantId = TenantContext.scopeOrAllForSuperAdmin();
        return tenantId == null ? productRepo.findAll() : productRepo.findByTenantId(tenantId);
    }

    /** A product the caller owns, or null when it is missing or someone else's. */
    private com.priceintel.backend.entity.Product tenantProductOrNull(Long productId) {
        com.priceintel.backend.entity.Product product =
                productRepo.findById(productId).orElse(null);
        Long tenantId = TenantContext.scopeOrAllForSuperAdmin();
        if (product == null || (tenantId != null && !tenantId.equals(product.getTenantId()))) {
            return null;
        }
        return product;
    }

    @Transactional
    public int generateAll() {
        int created = 0;
        for (com.priceintel.backend.entity.Product p : visibleProducts()) {
            if (repo.existsByProductIdAndStatus(p.getId(), RecommendationStatus.DRAFT)) {
                continue; // already has a pending draft
            }
            try {
                generate(p.getId(), null);
                created++;
            } catch (RuntimeException e) {
                log.debug("Skipping recommendation for product {}: {}", p.getId(), e.getMessage());
            }
        }
        log.info("Generated {} draft recommendations across the catalog", created);
        return created;
    }

    /**
     * Puts a draft forward for someone to sign off (FR-REC-002).
     *
     * <p>Optional step. A team that approves its own drafts can go straight to
     * {@link #approve}; this exists for the case where proposing and
     * authorising are different people, and gives the approver a queue to work
     * from rather than a list of everything.</p>
     */
    @Transactional
    public RecommendationResponse submitForApproval(Long id) {
        return transition(id, RecommendationStatus.PENDING_APPROVAL, "submit",
                RecommendationStatus.DRAFT);
    }

    @Transactional
    public RecommendationResponse approve(Long id) {
        // From either state: a draft can be approved directly by someone with
        // the authority, or picked up from the pending queue.
        return transition(id, RecommendationStatus.APPROVED, "approve",
                RecommendationStatus.DRAFT, RecommendationStatus.PENDING_APPROVAL);
    }

    @Transactional
    public RecommendationResponse publish(Long id) {
        PriceRecommendation candidate = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recommendation not found: " + id));

        // Cooldown is checked before the state changes, so a refusal leaves the
        // recommendation approved and publishable later rather than consuming it.
        var policy = policyService.resolve(candidate.getProductId(), null);
        String blocked = policyService.cooldownBlock(policy, lastPublishedAt(candidate.getProductId()));
        if (blocked != null) {
            throw new BadRequestException(blocked);
        }

        // Publishing to a marketplace requires an enabled channel + guardrails; for
        // now we mark it PUBLISHED (no automatic marketplace price push — FRD out of scope).
        RecommendationResponse published = transition(id, RecommendationStatus.PUBLISHED, "publish",
                RecommendationStatus.APPROVED);
        // Adopt the recommended price as the product's current selling price, so
        // Margin / break-even / the next recommendation all use the fresh price.
        PriceRecommendation rec = repo.findById(id).orElseThrow();
        if (rec.getRecommendedPrice() != null) {
            productRepo.findById(rec.getProductId()).ifPresent(p -> {
                // Captured before the change, because rollback needs somewhere
                // to go back to and nobody should have to remember it.
                rec.setPreviousPrice(p.getOurPrice());
                p.setOurPrice(rec.getRecommendedPrice());
                productRepo.save(p);
                log.info("Published recommendation {}: product {} {} -> {}",
                        id, rec.getProductId(), rec.getPreviousPrice(), rec.getRecommendedPrice());
            });
            rec.setPublishedAt(java.time.Instant.now());
            repo.save(rec);
        }
        return published;
    }

    /**
     * Undoes a published price, restoring what it replaced (FR-REC-002).
     *
     * <p>Deliberately not a new recommendation: this is the reversal of one that
     * should not have gone live, and generating a fresh one would re-derive the
     * same answer from the same data. The previous price is restored exactly.</p>
     */
    @Transactional
    public RecommendationResponse rollback(Long id) {
        PriceRecommendation rec = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recommendation not found: " + id));
        if (rec.getStatus() != RecommendationStatus.PUBLISHED) {
            throw new BadRequestException(
                    "Only a published recommendation can be rolled back; this one is "
                            + rec.getStatus() + ".");
        }
        if (rec.getRolledBackAt() != null) {
            throw new BadRequestException("This recommendation has already been rolled back.");
        }
        if (rec.getPreviousPrice() == null) {
            throw new BadRequestException(
                    "There is no earlier price recorded for this product, so there is nothing to "
                            + "restore. Set the price by hand instead.");
        }
        productRepo.findById(rec.getProductId()).ifPresent(p -> {
            log.info("Rolling back recommendation {}: product {} {} -> {}",
                    id, rec.getProductId(), p.getOurPrice(), rec.getPreviousPrice());
            p.setOurPrice(rec.getPreviousPrice());
            productRepo.save(p);
        });
        rec.setRolledBackAt(java.time.Instant.now());
        // Reverted, not approved and not draft — the record stays honest about
        // having been live, which an audit needs and a re-run would erase.
        rec.setStatus(RecommendationStatus.REJECTED);
        repo.save(rec);
        return toResponse(rec);
    }

    /** When this product's price was last published, for the cooldown check. */
    private java.time.Instant lastPublishedAt(Long productId) {
        return repo.findByProductIdOrderByCreatedAtDesc(productId).stream()
                .filter(r -> r.getPublishedAt() != null && r.getRolledBackAt() == null)
                .map(PriceRecommendation::getPublishedAt)
                .max(java.time.Instant::compareTo)
                .orElse(null);
    }

    private String describeBound(PricingPolicyService.Bound bound) {
        return switch (bound) {
            case FLOOR_PRICE -> "price floor";
            case FLOOR_MARGIN -> "margin floor";
            case CEILING -> "price ceiling";
            case MAX_CHANGE -> "maximum change per cycle";
            case NONE -> "policy";
        };
    }

    @Transactional
    public RecommendationResponse reject(Long id) {
        return transition(id, RecommendationStatus.REJECTED, "reject",
                RecommendationStatus.DRAFT, RecommendationStatus.PENDING_APPROVAL,
                RecommendationStatus.APPROVED);
    }

    // ---------- helpers ----------

    private RecommendationResponse transition(Long id, RecommendationStatus to, String action,
                                              RecommendationStatus... allowedFrom) {
        PriceRecommendation rec = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Recommendation not found: " + id));
        boolean ok = false;
        for (RecommendationStatus s : allowedFrom) {
            if (rec.getStatus() == s) {
                ok = true;
                break;
            }
        }
        if (!ok) {
            throw new BadRequestException("Cannot " + action + " a recommendation in state " + rec.getStatus());
        }
        rec.setStatus(to);
        return toResponse(repo.save(rec));
    }

    private BigDecimal clamp(BigDecimal price, RecommendationRequest r) {
        if (price == null) {
            return null;
        }
        if (r != null && r.getFloorPrice() != null && price.compareTo(r.getFloorPrice()) < 0) {
            price = r.getFloorPrice();
        }
        if (r != null && r.getCeilingPrice() != null && price.compareTo(r.getCeilingPrice()) > 0) {
            price = r.getCeilingPrice();
        }
        return price.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * How much this recommendation should be trusted.
     *
     * <p>A confirmed match counts for more than an unreviewed candidate. The
     * earlier version counted listings regardless of status, so accepting a
     * match — the reviewer's explicit statement that the comparison is sound —
     * moved the number not at all. Reviewing was invisible in the output, which
     * makes it look pointless.</p>
     *
     * <p>Costs matter more than either: without them the margin figures in the
     * recommendation are not computed at all.</p>
     */
    private BigDecimal confidence(int totalCompetitors, int confirmedCompetitors, boolean hasCosts) {
        int unreviewed = Math.max(totalCompetitors - confirmedCompetitors, 0);
        int c = 30
                + Math.min(confirmedCompetitors, 5) * 10
                + Math.min(unreviewed, 5) * 4
                + (hasCosts ? 15 : 0);
        return BigDecimal.valueOf(Math.min(c, 95));
    }

    private String rationale(BigDecimal price, BigDecimal median, BigDecimal breakEven,
                             BigDecimal margin, int competitors, BigDecimal targetMargin) {
        StringBuilder sb = new StringBuilder();
        sb.append("Recommended ").append(price);
        if (median != null) {
            sb.append(" — aligned to the competitor median (").append(median)
              .append(") across ").append(competitors).append(" listing(s)");
        }
        if (breakEven != null) {
            sb.append(", kept above break-even (").append(breakEven).append(")");
        }
        if (margin != null) {
            sb.append(". Expected contribution margin ~").append(margin).append("%")
              .append(" (target ").append(targetMargin).append("%).");
        } else {
            sb.append(". Add a cost profile to see the expected margin.");
        }
        // A single competitor makes "median" a grand word for "their price".
        // Saying so stops the figure being read as a market consensus.
        if (competitors == 1) {
            sb.append(" Based on one competitor, so this matches their price rather than "
                    + "a market range — confirm more comparables before relying on it.");
        }
        return sb.toString();
    }

    private RecommendationResponse toResponse(PriceRecommendation rec) {
        return toResponse(rec, null);
    }

    private RecommendationResponse toResponse(PriceRecommendation rec,
                                              com.priceintel.backend.entity.Product product) {
        BigDecimal currentPrice = product != null ? product.getOurPrice() : null;
        return RecommendationResponse.builder()
                .id(rec.getId()).productId(rec.getProductId())
                .productTitle(product != null ? product.getTitle() : null)
                .currentPrice(currentPrice)
                .recommendedPrice(rec.getRecommendedPrice())
                .riskLevel(risk(currentPrice, rec.getRecommendedPrice()))
                .currency(rec.getCurrency())
                .expectedMarginPct(rec.getExpectedMarginPct()).breakEvenPrice(rec.getBreakEvenPrice())
                .competitorMedian(rec.getCompetitorMedian()).confidence(rec.getConfidence())
                .rationale(rec.getRationale()).status(rec.getStatus().name())
                .expiresAt(rec.getExpiresAt()).createdAt(rec.getCreatedAt())
                .unboundedPrice(rec.getUnboundedPrice()).boundApplied(rec.getBoundApplied())
                .previousPrice(rec.getPreviousPrice())
                .publishedAt(rec.getPublishedAt()).rolledBackAt(rec.getRolledBackAt())
                .rollbackable(rec.getStatus() == RecommendationStatus.PUBLISHED
                        && rec.getRolledBackAt() == null && rec.getPreviousPrice() != null)
                .build();
    }

    /** Risk = how far the recommended price moves from the current price. */
    private String risk(BigDecimal current, BigDecimal recommended) {
        if (current == null || recommended == null || current.signum() == 0) {
            return "UNKNOWN";
        }
        double delta = recommended.subtract(current).abs().doubleValue() / current.doubleValue();
        if (delta < 0.05) {
            return "LOW";
        }
        if (delta < 0.12) {
            return "MEDIUM";
        }
        return "HIGH";
    }
}
