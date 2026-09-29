package com.priceintel.backend.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.priceintel.backend.constants.AlertCondition;
import com.priceintel.backend.dto.request.AlertRuleRequest;
import com.priceintel.backend.dto.request.BulkAlertRuleRequest;
import com.priceintel.backend.dto.response.AlertRuleResponse;
import com.priceintel.backend.dto.response.BulkAlertRuleResponse;
import com.priceintel.backend.entity.AlertRule;
import com.priceintel.backend.entity.Product;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.exception.ResourceNotFoundException;
import com.priceintel.backend.repository.AlertRuleRepository;
import com.priceintel.backend.repository.CompetitorListingRepository;
import com.priceintel.backend.repository.ProductRepository;
import com.priceintel.backend.security.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Alert-rule management (FR-ALERT-001) — the "Track price" feature. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertRuleService {

    /**
     * How many products one bulk request may cover.
     *
     * <p>Large enough for "select the whole category", small enough that a
     * mis-typed request cannot write tens of thousands of rows inside one
     * transaction and hold a lock while it does.</p>
     */
    private static final int MAX_BULK_PRODUCTS = 500;

    private final AlertRuleRepository repo;
    private final ProductRepository productRepo;
    private final CompetitorListingRepository listingRepo;

    // ---------- single ----------

    @Transactional
    public AlertRuleResponse create(AlertRuleRequest r) {
        // Ownership, not mere existence. The previous check accepted any product
        // id that existed anywhere, so one client could attach a rule to another
        // client's product and receive alerts about their prices.
        Product product = tenantProductOrNull(r.getProductId());
        if (product == null) {
            throw new ResourceNotFoundException("Product not found: " + r.getProductId());
        }
        AlertRule rule = repo.save(build(product.getId(), r.getCondition(), r.getThresholdType(),
                r.getThresholdValue(), r.getNote(), r.getChannels()));
        return toResponse(rule);
    }

    @Transactional(readOnly = true)
    public List<AlertRuleResponse> listByProduct(Long productId) {
        if (tenantProductOrNull(productId) == null) {
            // Missing and someone else's give the same empty answer — anything
            // else would confirm which product ids exist.
            return List.of();
        }
        return repo.findByProductIdOrderByCreatedAtDesc(productId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AlertRuleResponse> listForTenant() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return List.of();
        }
        return repo.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public void delete(Long id) {
        AlertRule rule = tenantRuleOrNull(id);
        if (rule == null) {
            // Same answer whether it never existed or belongs to someone else —
            // a distinct "forbidden" would confirm that the id is real.
            throw new ResourceNotFoundException("Alert rule not found: " + id);
        }
        repo.delete(rule);
    }

    /** Pause or resume one rule. A paused rule is kept and simply never fires. */
    @Transactional
    public AlertRuleResponse setActive(Long id, boolean active) {
        AlertRule rule = tenantRuleOrNull(id);
        if (rule == null) {
            throw new ResourceNotFoundException("Alert rule not found: " + id);
        }
        rule.setActive(active);
        return toResponse(repo.save(rule));
    }

    // ---------- bulk ----------

    /**
     * Applies one alert rule to many products (FR-ALERT-001).
     *
     * <p>Every product sent comes back with a row saying what happened to it.
     * The alternative — creating them one at a time from the browser — leaves a
     * half-finished batch behind whenever one call fails, and no way to tell
     * which ones landed.</p>
     *
     * <p>Not all-or-nothing on purpose: of fifty products, two already having
     * this alert is not a reason to refuse the other forty-eight. Only a request
     * that is malformed as a whole is rejected outright.</p>
     */
    @Transactional
    public BulkAlertRuleResponse createBulk(BulkAlertRuleRequest r) {
        // Duplicates within the request itself, kept in the order sent so the
        // result reads in the same order as the list the user selected.
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(
                r.getProductIds().stream().filter(java.util.Objects::nonNull).toList()));
        if (ids.isEmpty()) {
            throw new BadRequestException("Select at least one product.");
        }
        if (ids.size() > MAX_BULK_PRODUCTS) {
            throw new BadRequestException("Too many products in one request: " + ids.size()
                    + ". The limit is " + MAX_BULK_PRODUCTS
                    + " — split the selection and send it in batches.");
        }
        // Validated once for the whole batch. A bad channel name is a property of
        // the request, not of any one product, so it fails the request rather
        // than appearing as fifty identical per-product failures.
        String channels = joinChannels(r.getChannels());

        // Three lookups for the whole batch instead of three per product.
        Map<Long, Product> owned = new HashMap<>();
        for (Product p : productRepo.findAllById(ids)) {
            if (ownedByCaller(p)) {
                owned.put(p.getId(), p);
            }
        }
        Set<Long> alreadyHas = new HashSet<>();
        Map<Long, Long> existingRuleIds = new HashMap<>();
        for (AlertRule existing : repo.findByProductIdInAndCondition(owned.keySet(), r.getCondition())) {
            if (alreadyHas.add(existing.getProductId())) {
                existingRuleIds.put(existing.getProductId(), existing.getId());
            }
        }

        List<BulkAlertRuleResponse.Row> rows = new ArrayList<>(ids.size());
        List<AlertRule> toSave = new ArrayList<>();
        int created = 0;
        int skipped = 0;
        int failed = 0;
        int withoutCompetitors = 0;

        for (Long productId : ids) {
            Product product = owned.get(productId);
            if (product == null) {
                skipped++;
                rows.add(row(productId, null, "NOT_FOUND", null, null,
                        "No such product in your catalogue.", false));
                continue;
            }
            if (alreadyHas.contains(productId)) {
                skipped++;
                rows.add(row(productId, product.getTitle(), "SKIPPED_DUPLICATE",
                        null, existingRuleIds.get(productId),
                        "Already has a " + label(r.getCondition()) + " alert.", false));
                continue;
            }

            boolean noCompetitors = !hasSomethingToFireOn(productId);
            if (noCompetitors && r.isSkipProductsWithoutCompetitors()) {
                skipped++;
                rows.add(row(productId, product.getTitle(), "SKIPPED_NO_COMPETITORS",
                        null, null,
                        "No competitor listings yet, so this alert could never fire.", false));
                continue;
            }

            try {
                AlertRule rule = build(productId, r.getCondition(), r.getThresholdType(),
                        r.getThresholdValue(), r.getNote(), null);
                rule.setChannels(channels);
                toSave.add(rule);
                created++;
                if (noCompetitors) {
                    withoutCompetitors++;
                }
                rows.add(row(productId, product.getTitle(), "CREATED", null, null,
                        noCompetitors
                                ? "Created, but it cannot fire until this product has competitors."
                                : null,
                        noCompetitors));
            } catch (RuntimeException e) {
                failed++;
                log.warn("Bulk alert creation failed for product {}: {}", productId, e.getMessage());
                rows.add(row(productId, product.getTitle(), "FAILED", null, null,
                        e.getMessage(), false));
            }
        }

        // One insert batch, and the ids only exist after it.
        List<AlertRule> saved = repo.saveAll(toSave);
        Map<Long, Long> ruleIdByProduct = new HashMap<>();
        for (AlertRule rule : saved) {
            ruleIdByProduct.putIfAbsent(rule.getProductId(), rule.getId());
        }
        for (BulkAlertRuleResponse.Row row : rows) {
            if ("CREATED".equals(row.getOutcome())) {
                row.setRuleId(ruleIdByProduct.get(row.getProductId()));
            }
        }

        log.info("Bulk alert rules: {} requested, {} created, {} skipped, {} failed (condition {})",
                ids.size(), created, skipped, failed, r.getCondition());

        return BulkAlertRuleResponse.builder()
                .requested(ids.size()).created(created).skipped(skipped).failed(failed)
                .createdWithoutCompetitors(withoutCompetitors)
                .results(rows)
                .build();
    }

    /**
     * Deletes many rules, ignoring any the caller does not own.
     *
     * @return how many were actually deleted
     */
    @Transactional
    public int deleteBulk(List<Long> ruleIds) {
        List<AlertRule> rules = tenantRules(ruleIds);
        repo.deleteAll(rules);
        log.info("Bulk delete: {} of {} alert rule(s) removed", rules.size(), ruleIds.size());
        return rules.size();
    }

    /**
     * Pauses or resumes many rules at once.
     *
     * <p>Pausing keeps the rule and its history; deleting throws both away. The
     * two are offered separately because "stop telling me about this for now" and
     * "I never want this rule again" are different intentions.</p>
     *
     * @return how many were changed
     */
    @Transactional
    public int setActiveBulk(List<Long> ruleIds, boolean active) {
        List<AlertRule> rules = tenantRules(ruleIds);
        rules.forEach(rule -> rule.setActive(active));
        repo.saveAll(rules);
        log.info("Bulk {}: {} of {} alert rule(s)", active ? "resume" : "pause",
                rules.size(), ruleIds.size());
        return rules.size();
    }

    // ---------- helpers ----------

    private AlertRule build(Long productId, AlertCondition condition, String thresholdType,
                            java.math.BigDecimal thresholdValue, String note, List<String> channels) {
        return AlertRule.builder()
                .productId(productId)
                .tenantId(TenantContext.getTenantId())
                .condition(condition)
                .thresholdType(thresholdType != null ? thresholdType : "PERCENT")
                .thresholdValue(thresholdValue)
                .note(note)
                .channels(joinChannels(channels))
                .active(true)
                .build();
    }

    private BulkAlertRuleResponse.Row row(Long productId, String title, String outcome,
                                          Long ruleId, Long existingRuleId,
                                          String message, boolean cannotFireYet) {
        return BulkAlertRuleResponse.Row.builder()
                .productId(productId).productTitle(title).outcome(outcome)
                .ruleId(ruleId).existingRuleId(existingRuleId)
                .message(message).cannotFireYet(cannotFireYet)
                .build();
    }

    /**
     * Whether this product has any listing an alert could actually fire on.
     *
     * <p>Exactly what {@code marketPrices()} reads: not {@code REJECTED}, and
     * carrying a price. Three tempting definitions are all wrong, and two of
     * them were shipped before this comment was written.</p>
     *
     * <ul>
     *   <li>Counting <em>every</em> row lets a product whose listings were all
     *       rejected look covered, and the rule sits silent forever.</li>
     *   <li>Counting non-rejected rows still counts listings that were found on
     *       a marketplace but never successfully priced. A row with no price has
     *       nothing to compare, however well matched — this is the case that
     *       reached the client.</li>
     *   <li>Counting only confirmed matches warns about products that fire
     *       perfectly well today, because the engine evaluates unreviewed
     *       candidates too; that trains people to ignore the warning.</li>
     * </ul>
     *
     * <p>If the engine's definition ever changes, this has to change with it.</p>
     */
    private boolean hasSomethingToFireOn(Long productId) {
        return listingRepo.countByProductIdAndMatchStatusNotAndLastPriceIsNotNull(
                productId, com.priceintel.backend.constants.MatchStatus.REJECTED) > 0;
    }

    /** The rules of these ids that the caller owns. Others are silently absent. */
    private List<AlertRule> tenantRules(List<Long> ruleIds) {
        if (ruleIds == null || ruleIds.isEmpty()) {
            throw new BadRequestException("Select at least one alert rule.");
        }
        if (ruleIds.size() > MAX_BULK_PRODUCTS) {
            throw new BadRequestException("Too many rules in one request: " + ruleIds.size()
                    + ". The limit is " + MAX_BULK_PRODUCTS + ".");
        }
        Long tenantId = TenantContext.getTenantId();
        // The platform owner has no tenant of their own and may act on any rule.
        return tenantId == null
                ? repo.findByIdIn(ruleIds) : repo.findByIdInAndTenantId(ruleIds, tenantId);
    }

    /** A product the caller owns, or null when it is missing or someone else's. */
    private Product tenantProductOrNull(Long productId) {
        if (productId == null) {
            return null;
        }
        Product product = productRepo.findById(productId).orElse(null);
        return product != null && ownedByCaller(product) ? product : null;
    }

    private AlertRule tenantRuleOrNull(Long id) {
        AlertRule rule = repo.findById(id).orElse(null);
        if (rule == null) {
            return null;
        }
        Long tenantId = TenantContext.getTenantId();
        return tenantId == null || tenantId.equals(rule.getTenantId()) ? rule : null;
    }

    private boolean ownedByCaller(Product product) {
        Long tenantId = TenantContext.scopeOrAllForSuperAdmin();
        return tenantId == null || tenantId.equals(product.getTenantId());
    }

    /** "price drop" from PRICE_DROP, for a message a person reads. */
    private String label(AlertCondition condition) {
        return condition.name().toLowerCase().replace('_', ' ');
    }

    /**
     * Stored comma-separated; validated on the way in so a typo fails at save
     * time rather than silently sending nowhere on every alert.
     */
    private String joinChannels(java.util.List<String> channels) {
        if (channels == null || channels.isEmpty()) {
            return null;
        }
        java.util.Set<String> allowed = java.util.Set.of("IN_APP", "EMAIL", "SLACK", "TEAMS");
        return String.join(",", channels.stream()
                .map(c -> c == null ? "" : c.trim().toUpperCase())
                .peek(c -> {
                    if (!allowed.contains(c)) {
                        throw new com.priceintel.backend.exception.BadRequestException(
                                "Unknown channel: " + c + ". Use IN_APP, EMAIL, SLACK or TEAMS.");
                    }
                })
                .distinct().toList());
    }

    private java.util.List<String> splitChannels(String channels) {
        return channels == null || channels.isBlank()
                ? java.util.List.of()
                : java.util.Arrays.asList(channels.split(","));
    }

    private AlertRuleResponse toResponse(AlertRule r) {
        return AlertRuleResponse.builder()
                .id(r.getId()).productId(r.getProductId()).condition(r.getCondition().name())
                .thresholdType(r.getThresholdType()).thresholdValue(r.getThresholdValue())
                .active(r.isActive()).note(r.getNote())
                .channels(splitChannels(r.getChannels()))
                .lastFiredAt(r.getLastFiredAt()).triggerCount(r.getTriggerCount())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
