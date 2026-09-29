package com.priceintel.backend.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceintel.backend.ai.AiCompletion;
import com.priceintel.backend.ai.AiCompletionRequest;
import com.priceintel.backend.ai.AiGateway;
import com.priceintel.backend.entity.AiMatchVerdict;
import com.priceintel.backend.entity.CompetitorListing;
import com.priceintel.backend.entity.Product;
import com.priceintel.backend.repository.AiMatchVerdictRepository;
import com.priceintel.backend.repository.CompetitorListingRepository;
import com.priceintel.backend.repository.ProductRepository;
import com.priceintel.backend.security.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Asks the model whether a candidate listing is really the same product, and
 * remembers the answer (FR-MATCH-003).
 *
 * <p>The rules engine compares strings, so it scores a refrigerator door shelf
 * at 20% against a refrigerator — low, but indistinguishable from a genuine
 * match described in different words. A model reads the meaning and says
 * "component, not appliance". That distinction is the whole value here.</p>
 *
 * <p>Advisory only. The verdict is stored alongside the deterministic score for
 * a reviewer to act on; nothing here writes {@code match_status}. Until the two
 * have been compared over a few hundred real candidates there is no evidence
 * that automatic acceptance would be safe, and a wrong automatic rejection is
 * invisible.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiMatchAdvisorService {

    /**
     * Bumped whenever the prompt changes. Verdicts are keyed by it, so old
     * answers are never served as though the current prompt produced them.
     */
    /**
     * Bumped when the prompt changes. Verdicts are cached against it, so a bump
     * re-judges pairs decided under the previous wording — which is the point:
     * the old prompt did not say that a different size or colour is a different
     * product, and called them matches.
     */
    private static final String PROMPT_VERSION = "match-v3";

    private static final String SYSTEM_PROMPT = """
            You judge whether two e-commerce listings are the SAME purchasable product.

            Same product means a buyer would receive an equivalent item. Consider brand,
            model, capacity/size, colour, pack quantity and condition.

            A DIFFERENT VARIANT IS A DIFFERENT PRODUCT. Size, colour, capacity, length
            and wattage each identify one variant: size 9.5 is not size 11.5, Black is
            not White, 256GB is not 512GB. They sell at different prices and go out of
            stock separately.

            Decide NOT_MATCH when the candidate is:
            - a different variant: size, colour, capacity, length or wattage
            - an accessory, spare part or replacement component for the product
            - a different model, generation or pack size
            - a bundle containing the product plus other items

            Reply with ONLY a JSON object:
            {"decision":"MATCH"|"NOT_MATCH"|"UNCERTAIN","score":0-100,"reason":"one sentence"}

            score is HOW LIKELY THE TWO ARE THE SAME PRODUCT, never your confidence
            in the decision. A candidate you are certain is a different product scores
            near 0, not near 100. A certain match scores near 100. Keep score
            consistent with decision: NOT_MATCH must score below 50, MATCH above 50.

            Use UNCERTAIN when the titles are too sparse to tell. Do not guess.
            """;

    private final AiGateway aiGateway;
    private final AiMatchVerdictRepository verdictRepo;
    private final ProductRepository productRepo;
    private final CompetitorListingRepository listingRepo;
    private final ObjectMapper objectMapper;

    /** Whether an AI provider is configured at all. */
    public boolean isAvailable() {
        return aiGateway.isAvailable();
    }

    /**
     * Judges every candidate of a product that has not been judged already.
     *
     * <p>Cached verdicts are returned untouched. Only genuinely new pairs cost a
     * call, which is what keeps a re-visit free rather than another pass through
     * the minute's token budget.</p>
     *
     * @param limit most calls to spend in this pass, so one product cannot
     *              consume the whole quota
     */
    @Transactional
    public List<AiMatchVerdict> adviseForProduct(Long productId, int limit) {
        Product product = productRepo.findById(productId).orElse(null);
        if (product == null || !aiGateway.isAvailable()) {
            return List.of();
        }
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null && !tenantId.equals(product.getTenantId())) {
            return List.of();
        }

        Map<Long, AiMatchVerdict> existing = verdictRepo
                .findByProductIdAndPromptVersion(productId, PROMPT_VERSION).stream()
                .collect(Collectors.toMap(AiMatchVerdict::getListingId, Function.identity(),
                        (a, b) -> a));

        List<AiMatchVerdict> results = new ArrayList<>(existing.values());
        int spent = 0;
        for (CompetitorListing listing : listingRepo.findByProductIdOrderByCreatedAtDesc(productId)) {
            if (existing.containsKey(listing.getId())) {
                continue;
            }
            if (spent >= limit) {
                log.info("AI advice for product {} stopped at the {}-call limit; "
                        + "{} candidate(s) still unjudged", productId, limit,
                        listingRepo.findByProductIdOrderByCreatedAtDesc(productId).size()
                                - existing.size() - spent);
                break;
            }
            judge(product, listing).ifPresent(results::add);
            spent++;
        }
        return results;
    }

    /** Verdicts already held for these listings — no calls, for rendering a list. */
    @Transactional(readOnly = true)
    public Map<Long, AiMatchVerdict> cachedFor(List<Long> listingIds) {
        if (listingIds == null || listingIds.isEmpty()) {
            return Map.of();
        }
        return verdictRepo.findByListingIdIn(listingIds).stream()
                .filter(v -> PROMPT_VERSION.equals(v.getPromptVersion()))
                .collect(Collectors.toMap(AiMatchVerdict::getListingId, Function.identity(),
                        (a, b) -> a));
    }

    // ---------- one judgement ----------

    private Optional<AiMatchVerdict> judge(Product product, CompetitorListing listing) {
        try {
            AiCompletion completion = aiGateway.complete(AiCompletionRequest.builder()
                    .systemPrompt(SYSTEM_PROMPT)
                    .userPrompt(describe(product, listing))
                    .temperature(0.0)
                    .jsonOnly(true)
                    .maxTokens(300)
                    .build());

            JsonNode parsed = objectMapper.readTree(completion.getContent());
            AiMatchVerdict verdict = AiMatchVerdict.builder()
                    .productId(product.getId())
                    .listingId(listing.getId())
                    .marketplace(listing.getMarketplace())
                    .marketplaceItemId(listing.getMarketplaceItemId())
                    .decision(decisionOf(parsed))
                    .score(coherentScore(parsed, decisionOf(parsed)))
                    .reason(truncate(parsed.path("reason").asText(null)))
                    .provider(completion.getProvider())
                    .model(completion.getModel())
                    .promptVersion(PROMPT_VERSION)
                    .totalTokens(completion.getTotalTokens())
                    .build();
            return Optional.of(verdictRepo.save(verdict));

        } catch (Exception e) {
            // One unreadable answer or one rate-limited call must not abandon
            // the rest of the queue; the pair is simply left unjudged and will
            // be retried on the next pass.
            log.warn("AI verdict failed for listing {} (product {}): {}",
                    listing.getId(), product.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * The comparison, as text.
     *
     * <p>Deliberately excludes cost, margin and our own price: they are
     * confidential, they leave the building on every call, and none of them
     * bears on whether two listings are the same product.</p>
     */
    private String describe(Product product, CompetitorListing listing) {
        StringBuilder sb = new StringBuilder();
        sb.append("PRODUCT\n");
        sb.append("title: ").append(product.getTitle()).append('\n');
        appendIfPresent(sb, "brand", product.getBrand());
        appendIfPresent(sb, "condition", product.getCondition() == null
                ? null : product.getCondition().name());
        if (product.getPackQuantity() != null) {
            sb.append("pack quantity: ").append(product.getPackQuantity()).append('\n');
        }
        sb.append("\nCANDIDATE\n");
        sb.append("title: ").append(listing.getTitle()).append('\n');
        appendIfPresent(sb, "seller", listing.getSeller());
        appendIfPresent(sb, "condition", listing.getCondition());
        sb.append("\nAre these the same purchasable product?");
        return sb.toString();
    }

    private void appendIfPresent(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append(": ").append(value).append('\n');
        }
    }

    private String decisionOf(JsonNode parsed) {
        return normaliseDecision(parsed.path("decision").asText("UNCERTAIN"));
    }

    /**
     * The score, guarded against the model reporting its own confidence instead.
     *
     * <p>Models readily answer {@code NOT_MATCH} with a score of 90, meaning
     * "90% sure it is not". Rendered beside a rejected row that reads as a
     * strong match — the opposite of the truth — so a score that contradicts its
     * decision is inverted rather than shown. The prompt asks for the right
     * thing; this is the belt to that braces.</p>
     */
    private Integer coherentScore(JsonNode parsed, String decision) {
        if (!parsed.path("score").isNumber()) {
            return null;
        }
        int score = Math.max(0, Math.min(100, parsed.get("score").asInt()));
        if ("NOT_MATCH".equals(decision) && score > 50) {
            return 100 - score;
        }
        if ("MATCH".equals(decision) && score < 50) {
            return 100 - score;
        }
        return score;
    }

    private String normaliseDecision(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase().replace(' ', '_');
        return switch (value) {
            case "MATCH", "NOT_MATCH", "UNCERTAIN" -> value;
            // Anything unrecognised is treated as "cannot tell" rather than
            // guessed into a decision the model did not make.
            default -> "UNCERTAIN";
        };
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 1000 ? s.substring(0, 1000) : s;
    }
}
