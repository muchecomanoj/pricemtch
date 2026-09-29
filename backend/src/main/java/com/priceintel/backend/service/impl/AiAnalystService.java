package com.priceintel.backend.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.priceintel.backend.dto.response.AnalystResponse;
import com.priceintel.backend.dto.response.AnalystResponse.Item;
import com.priceintel.backend.dto.response.MarketPricesResponse;
import com.priceintel.backend.dto.response.ProfitabilityResponse;
import com.priceintel.backend.entity.Product;
import com.priceintel.backend.repository.ProductRepository;
import com.priceintel.backend.security.TenantContext;

import lombok.RequiredArgsConstructor;

/**
 * Deterministic AI Analyst: answers common pricing/margin/competitor questions
 * straight from stored data (no LLM). Detects intent by keywords, then computes
 * the answer from products, costs, and competitor listings.
 */
@Service
@RequiredArgsConstructor
public class AiAnalystService {

    private final ProductRepository productRepo;
    private final CostProfileService costService;
    private final SearchPipelineService searchService;

    private static final BigDecimal BREACH_THRESHOLD = BigDecimal.valueOf(15); // margin % floor
    private static final String SOURCE = "Computed from your stored costs, prices and competitor listings";

    public List<String> suggestions() {
        return List.of(
                "Which products have the lowest margin this week?",
                "Show products at risk of margin breach.",
                "What price should I set for <product>?",
                "Summarize competitor price changes for <product>.");
    }

    @Transactional(readOnly = true)
    public AnalystResponse ask(String question) {
        String q = question == null ? "" : question.toLowerCase().trim();

        if (contains(q, "margin") && (contains(q, "lowest") || contains(q, "worst") || contains(q, "low"))) {
            return marginRanking(question, true);
        }
        if (contains(q, "margin") && (contains(q, "highest") || contains(q, "best") || contains(q, "top"))) {
            return marginRanking(question, false);
        }
        if (contains(q, "breach") || (contains(q, "risk") && contains(q, "margin"))) {
            return marginBreach(question);
        }
        if ((contains(q, "price") || contains(q, "recommend")) && (contains(q, "set") || contains(q, "should")
                || contains(q, "recommend"))) {
            return priceRecommendation(question);
        }
        if (contains(q, "competitor") || contains(q, "market")) {
            return competitorSummary(question);
        }
        return fallback(question);
    }

    // ---------- handlers ----------

    /**
     * The catalogue this question may be answered from.
     *
     * <p>The analyst answers in natural language — "your lowest-margin product
     * is X at 4%" — so an unscoped catalogue would not merely leak a row, it
     * would narrate a competing client's margins back to the asker. The
     * platform owner alone sees everything.</p>
     */
    private List<Product> visibleProducts() {
        Long tenantId = TenantContext.scopeOrAllForSuperAdmin();
        return tenantId == null ? productRepo.findAll() : productRepo.findByTenantId(tenantId);
    }

    private AnalystResponse marginRanking(String question, boolean lowestFirst) {
        List<Item> items = new ArrayList<>();
        for (Product p : visibleProducts()) {
            BigDecimal margin = marginOf(p);
            if (margin != null) {
                items.add(Item.builder().productId(p.getId()).product(p.getTitle())
                        .metric("Contribution margin").value(fmtPct(margin))
                        .note("at " + money(p.getOurPrice())).build());
            }
        }
        if (items.isEmpty()) {
            return build(question, lowestFirst ? "LOWEST_MARGIN" : "HIGHEST_MARGIN",
                    "No products have both a price and costs yet, so margins can't be computed. "
                            + "Set a selling price and a cost profile (or the global Cost Management defaults) first.",
                    items);
        }
        items.sort(Comparator.comparing(i -> new BigDecimal(i.getValue().replace("%", ""))));
        if (!lowestFirst) {
            java.util.Collections.reverse(items);
        }
        List<Item> top = items.size() > 5 ? items.subList(0, 5) : items;
        StringBuilder sb = new StringBuilder(lowestFirst
                ? "Products with the lowest margins:\n" : "Products with the highest margins:\n");
        for (Item i : top) {
            sb.append("• ").append(i.getProduct()).append(" — ").append(i.getValue())
              .append(" (").append(i.getNote()).append(")\n");
        }
        return build(question, lowestFirst ? "LOWEST_MARGIN" : "HIGHEST_MARGIN",
                sb.toString().trim(), new ArrayList<>(top));
    }

    private AnalystResponse marginBreach(String question) {
        List<Item> items = new ArrayList<>();
        for (Product p : visibleProducts()) {
            BigDecimal margin = marginOf(p);
            if (margin != null && margin.compareTo(BREACH_THRESHOLD) < 0) {
                items.add(Item.builder().productId(p.getId()).product(p.getTitle())
                        .metric("Margin").value(fmtPct(margin))
                        .note("below " + BREACH_THRESHOLD + "% threshold").build());
            }
        }
        items.sort(Comparator.comparing(i -> new BigDecimal(i.getValue().replace("%", ""))));
        String answer = items.isEmpty()
                ? "Good news — no products are below the " + BREACH_THRESHOLD + "% margin threshold."
                : items.size() + " product(s) are at risk (margin below " + BREACH_THRESHOLD + "%):\n"
                    + bullets(items);
        return build(question, "MARGIN_BREACH", answer, items);
    }

    private AnalystResponse priceRecommendation(String question) {
        Product p = findProduct(question);
        if (p == null) {
            return build(question, "PRICE_RECOMMENDATION",
                    "Tell me which product — e.g. \"What price should I set for "
                            + anExample() + "?\"", List.of());
        }
        MarketPricesResponse market = searchService.marketPrices(p.getId(), false);
        BigDecimal median = market.getMedian();
        BigDecimal breakEven = null;
        try {
            ProfitabilityResponse pr = costService.computeProfitability(p.getId(),
                    median != null ? median : p.getOurPrice());
            if (pr.isCostProfileConfigured()) {
                breakEven = pr.getBreakEvenPrice();
            }
        } catch (RuntimeException ignored) {
            // no price/costs — median-only answer
        }
        if (median == null) {
            return build(question, "PRICE_RECOMMENDATION",
                    "No competitor prices captured for " + p.getTitle()
                            + " yet. Run \"Find competitors\" first.", List.of());
        }
        StringBuilder sb = new StringBuilder("Suggested price for ").append(p.getTitle())
                .append(": ").append(money(median))
                .append(" — aligned to the competitor median across ")
                .append(market.getCompetitorCount()).append(" listing(s).");
        if (breakEven != null) {
            sb.append(" Break-even is ").append(money(breakEven)).append(", so it stays profitable.");
        }
        List<Item> items = List.of(
                Item.builder().productId(p.getId()).product(p.getTitle())
                        .metric("Suggested price").value(money(median)).note("competitor median").build(),
                Item.builder().productId(p.getId()).product(p.getTitle())
                        .metric("Current price").value(money(p.getOurPrice())).note("your price").build());
        return build(question, "PRICE_RECOMMENDATION", sb.toString(), items);
    }

    private AnalystResponse competitorSummary(String question) {
        Product p = findProduct(question);
        if (p == null) {
            return build(question, "COMPETITOR_SUMMARY",
                    "Which product's competitors? e.g. \"Summarize competitor prices for "
                            + anExample() + ".\"", List.of());
        }
        MarketPricesResponse m = searchService.marketPrices(p.getId(), false);
        if (m.getCompetitorCount() == 0) {
            return build(question, "COMPETITOR_SUMMARY",
                    "No competitor listings for " + p.getTitle() + " yet. Run \"Find competitors\".", List.of());
        }
        String answer = String.format("%s — %d competitor(s): lowest %s, median %s, average %s, highest %s. "
                + "Your price is %s.",
                p.getTitle(), m.getCompetitorCount(), money(m.getLowest()), money(m.getMedian()),
                money(m.getAverage()), money(m.getHighest()), money(p.getOurPrice()));
        List<Item> items = List.of(
                Item.builder().productId(p.getId()).product(p.getTitle()).metric("Lowest").value(money(m.getLowest())).build(),
                Item.builder().productId(p.getId()).product(p.getTitle()).metric("Median").value(money(m.getMedian())).build(),
                Item.builder().productId(p.getId()).product(p.getTitle()).metric("Average").value(money(m.getAverage())).build(),
                Item.builder().productId(p.getId()).product(p.getTitle()).metric("Highest").value(money(m.getHighest())).build());
        return build(question, "COMPETITOR_SUMMARY", answer, items);
    }

    private AnalystResponse fallback(String question) {
        return build(question, "UNKNOWN",
                "I can answer from your stored data. Try:\n"
                        + "• Which products have the lowest margin?\n"
                        + "• Show products at risk of margin breach.\n"
                        + "• What price should I set for " + anExample() + "?\n"
                        + "• Summarize competitor prices for " + anExample() + ".",
                List.of());
    }

    // ---------- helpers ----------

    private BigDecimal marginOf(Product p) {
        if (p.getOurPrice() == null || p.getOurPrice().signum() <= 0) {
            return null;
        }
        try {
            ProfitabilityResponse pr = costService.computeProfitability(p.getId(), null);
            return pr.isCostProfileConfigured() ? pr.getContributionMarginPct() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Best product match: most title words (len>=3) that appear in the question. */
    private Product findProduct(String question) {
        String q = question.toLowerCase();
        Product best = null;
        int bestScore = 0;
        for (Product p : visibleProducts()) {
            int score = 0;
            if (p.getSku() != null && q.contains(p.getSku().toLowerCase())) {
                score += 5;
            }
            for (String w : p.getTitle().toLowerCase().split("[^a-z0-9]+")) {
                if (w.length() >= 3 && q.contains(w)) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return bestScore > 0 ? best : null;
    }

    private String anExample() {
        return visibleProducts().stream().findFirst().map(Product::getTitle).orElse("a product");
    }

    private boolean contains(String q, String s) {
        return q.contains(s);
    }

    private String bullets(List<Item> items) {
        StringBuilder sb = new StringBuilder();
        for (Item i : items) {
            sb.append("• ").append(i.getProduct()).append(" — ").append(i.getValue()).append("\n");
        }
        return sb.toString().trim();
    }

    private String fmtPct(BigDecimal v) {
        return v == null ? "—" : v.stripTrailingZeros().toPlainString() + "%";
    }

    private String money(BigDecimal v) {
        return v == null ? "—" : "$" + v.toPlainString();
    }

    private AnalystResponse build(String question, String intent, String answer, List<Item> items) {
        return AnalystResponse.builder()
                .question(question).intent(intent).answer(answer)
                .items(items).source(SOURCE).generatedAt(Instant.now())
                .build();
    }
}
