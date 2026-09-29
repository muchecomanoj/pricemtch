package com.priceintel.backend.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceintel.backend.ai.AiCompletion;
import com.priceintel.backend.ai.AiCompletionRequest;
import com.priceintel.backend.ai.AiGateway;
import com.priceintel.backend.dto.request.ChannelSearchRequest;
import com.priceintel.backend.dto.response.MatchedListing;
import com.priceintel.backend.exception.AiException;
import com.priceintel.backend.marketplace.model.SearchResultItem;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Decides which of a search's results are actually the product that was
 * searched for (FR-MATCH-001).
 *
 * <p>A later step of one search rather than a search of its own. The marketplace
 * finds candidates; this says which are the same product. That distinction is
 * what string comparison cannot make — "AirPods 4 (Renewed)" is the same product
 * and "AirPods Pro 3" is not, and as text they are equally similar.</p>
 *
 * <p>Applies to identifier searches too. An ASIN says how a candidate was found,
 * not that it is acceptable: the same identifier family returns two-packs beside
 * singles and renewed units beside new ones. Skipping the check for exact
 * identifiers would skip it where a mistake costs most.</p>
 *
 * <h2>One call, not one per listing</h2>
 * <p>Every candidate goes in a single request. Judged separately this would be
 * ten calls and forty seconds against a per-minute token budget; batched it is
 * one call and about two seconds. Seeing the candidates together also sharpens
 * the distinctions — a Pro 3 next to a plain 4 is easier to tell apart than
 * either alone.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListingJudgeService {

    /** Bumped when the prompt changes, and recorded on every verdict. */
    static final String PROMPT_VERSION = "listing-judge-v2";

    private static final String SYSTEM_PROMPT = """
            You decide which marketplace listings are the SAME purchasable product as a
            reference product.

            Same product means a buyer would receive an equivalent item. Titles differ
            freely — "AirPods 4", "AirPods 4 Wireless Earbuds" and "Apple AirPods (4th
            Generation)" are all the same product. Judge the product, not the wording.

            A DIFFERENT VARIANT IS A DIFFERENT PRODUCT. Size, colour, capacity, length,
            wattage and flavour each identify one variant. Shoes in size 9.5 are not
            shoes in size 11.5; a Black/Asphalt is not a White; 256GB is not 512GB.
            They sell at different prices and go out of stock separately, so they are
            NOT_MATCH, with the differing attribute named in conflicts.
            The reference states the variant it wants. When a listing states a
            different one, say NOT_MATCH however similar the rest of the title is.

            MATCH        the same product and the same variant
            EQUIVALENT   the same product and variant in a different condition or pack,
                         e.g. Renewed, or a 2-pack of the same item
            NOT_MATCH    a different variant, model, generation or capacity; a bundle
                         with extras; or an accessory, case, cable or spare part
            UNCERTAIN    the title is too sparse to tell

            Reply with ONLY a JSON object. Two examples of the shape:
            {"results":[
              {"id":1,"decision":"MATCH","score":88,"confidence":0.9,
               "matchedAttributes":["brand","model","size","colour"],
               "conflicts":[],"missingEvidence":[],"reason":"one short sentence"},
              {"id":2,"decision":"NOT_MATCH","score":12,"confidence":0.8,
               "matchedAttributes":["brand","model"],
               "conflicts":[{"field":"size","productValue":"11.5",
                             "candidateValue":"9.5"}],
               "missingEvidence":["condition"],"reason":"one short sentence"}]}

            Each conflict gives the attribute AND both values, the right way round:
            productValue is what the REFERENCE product states, candidateValue is what
            the LISTING states. Reversing them tells the reader the opposite of what
            is true. "condition" alone says where to look; "New vs Renewed" is what
            decides whether the listing is comparable.

            score is how likely it is the same purchasable product, 0-100. Use the
            whole range and score each listing on its own merits — do not reuse one
            number for every listing:
              90-100  same product, and every stated variant attribute agrees
              70-89   same product, nothing contradicts, some attribute unstated
              50-69   probably the same, but the title is thin
              20-49   probably a different variant or model
              0-19    clearly a different product, variant or an accessory
            Keep it consistent with the decision: NOT_MATCH below 50, MATCH above 50.
            confidence is how much evidence you had, 0.0-1.0 — low when the title is bare.
            missingEvidence lists what the listing does not say, so a person can check it.
            Include every id you were given.
            """;

    private final AiGateway aiGateway;
    private final ObjectMapper objectMapper;
    private final com.priceintel.backend.ai.OpenAiProperties aiProps;

    /** Marks a listing the model never saw. */
    static final String RULES = "RULES";

    /** {@link Verdicts#unavailableReason()} when no AI provider is configured. */
    public static final String NOT_CONFIGURED = "NOT_CONFIGURED";

    /**
     * What a judging pass produced.
     *
     * <p>{@code matches} and {@code rejected} are null unless the model actually
     * judged, and hold only the listings it decided about. They mean "listings
     * the model ruled on", and there is no honest way to fill them when it never
     * ran: an unjudged listing is not a match. Filling them anyway is what let a
     * failed judge report twelve unrelated products as matches — the auto-accept
     * guard failing open, which is the one direction it must never fail.</p>
     */
    public record Verdicts(List<MatchedListing> matches, List<MatchedListing> rejected,
            boolean judgedByAi, int searchedCount, String unavailableReason) {

        /**
         * {@code NO_LISTINGS} nothing came back to judge; {@code NOT_JUDGED} the
         * model did not run; otherwise {@code FOUND} or {@code NONE_MATCHED}.
         *
         * <p>NOT_JUDGED is checked after NO_LISTINGS: an empty search is better
         * described by what the marketplace returned than by a judge that had
         * nothing to do.</p>
         */
        public String status() {
            if (searchedCount == 0) {
                return "NO_LISTINGS";
            }
            if (!judgedByAi) {
                return "NOT_JUDGED";
            }
            return matches.isEmpty() ? "NONE_MATCHED" : "FOUND";
        }

        /** True when trying again could plausibly change the outcome. */
        public boolean retryable() {
            // A missing provider is a configuration fact, not a transient one:
            // retrying will fail identically until someone sets the key.
            return !judgedByAi && unavailableReason != null
                    && !NOT_CONFIGURED.equals(unavailableReason);
        }
    }

    /**
     * Judges the results of a search against what was searched for.
     *
     * <p>Matches and rejects are returned separately rather than as one list the
     * caller must filter. Both are useful, but a single list invites a consumer
     * to forget the filter and present a rejected listing as a competitor —
     * exactly the mistake this exists to prevent.</p>
     */
    public Verdicts judge(ChannelSearchRequest request, List<SearchResultItem> items,
            String marketplace) {
        List<MatchedListing> candidates = new ArrayList<>();
        for (SearchResultItem item : items) {
            candidates.add(MatchedListing.builder()
                    // The item's own channel when it has one — results from
                    // several marketplaces arrive in one list, and labelling
                    // them all with a single value would file eBay listings
                    // under Amazon.
                    .marketplace(item.getMarketplace() != null
                            ? item.getMarketplace() : marketplace)
                    // Carried to the screen, so attaching this result can ask for
                    // the same storefront it was found in.
                    .storefront(com.priceintel.backend.utils.Storefront.resolve(
                            item.getStorefront(), item.getUrl(), item.getCurrency()))
                    .marketplaceItemId(item.getMarketplaceItemId())
                    .title(item.getTitle()).url(item.getUrl())
                    .seller(item.getSeller()).condition(item.getCondition())
                    .price(item.getPrice()).shipping(item.getShipping())
                    .currency(item.getCurrency())
                    // An estimate must stay labelled all the way to the screen.
                    // Stripped here, it would arrive looking like an observed
                    // price and be read as one.
                    .priceEstimated(item.isPriceEstimated())
                    .priceNote(item.getPriceNote())
                    .availability(item.getAvailability())
                    .identifiers(item.getIdentifiers())
                    .build());
        }
        return judgeListings(reference(request), candidates);
    }

    /** As above, for listings already in {@link MatchedListing} form. */
    public Verdicts judgeListings(String reference, List<MatchedListing> candidates) {
        if (candidates.isEmpty()) {
            return new Verdicts(null, null, false, 0, null);
        }
        String unavailable = apply(reference, candidates);
        if (unavailable != null) {
            // Nothing was judged, so neither list has anything to hold. The
            // listings still reach the caller as ordinary search results.
            return new Verdicts(null, null, false, candidates.size(), unavailable);
        }

        // Only what the model actually ruled on. Anything past MAX_JUDGED fell
        // back to rules, and belongs in neither list for the same reason.
        List<MatchedListing> judged = candidates.stream()
                .filter(l -> !RULES.equals(l.getJudgedBy())).toList();
        List<MatchedListing> matches = judged.stream()
                .filter(l -> !"NOT_MATCH".equals(l.getDecision()))
                .sorted(byScoreDescending()).toList();
        List<MatchedListing> rejected = judged.stream()
                .filter(l -> "NOT_MATCH".equals(l.getDecision()))
                .sorted(byScoreDescending()).toList();

        if (judged.size() < candidates.size()) {
            // Not fatal — the verdicts given are still sound — but it must not
            // pass unnoticed: the counts on screen change between two identical
            // searches, and without this line there is nothing to explain why.
            log.warn("Model returned verdicts for {} of {} listing(s); the rest are unjudged",
                    judged.size(), candidates.size());
        }

        // Each verdict, not just the tally. A rejection is a decision made on a
        // user's behalf, and "7 others were rejected" is unanswerable after the
        // fact without this — the verdicts live only in the response, and a
        // count cannot say which listing or why.
        if (log.isDebugEnabled()) {
            candidates.forEach(l -> log.debug("verdict {} {} score={} — {} | {}",
                    l.getMarketplaceItemId(), l.getDecision(), l.getScore(),
                    l.getTitle(), l.getReason()));
        }

        return new Verdicts(matches, rejected, true, candidates.size(), null);
    }

    /** The product being searched for, as the model sees it. */
    public String reference(ChannelSearchRequest request) {
        StringBuilder sb = new StringBuilder();
        appendIf(sb, "brand", request.getBrand());
        appendIf(sb, "title", request.getTitle());
        appendIf(sb, "condition", request.getCondition());
        if (request.getIdentifiers() != null) {
            request.getIdentifiers().forEach((k, v) -> appendIf(sb, k.toLowerCase(), v));
        }
        return sb.toString();
    }

    /** A sentence for the user, written to be shown as-is. */
    public String message(Verdicts v) {
        if (v.searchedCount() == 0) {
            return "No listings found for that search. Check the wording, or the product "
                    + "may not be sold in the regions we can reach.";
        }
        if (!v.judgedByAi()) {
            String why = NOT_CONFIGURED.equals(v.unavailableReason())
                    ? "no AI reviewer is configured"
                    : "the AI reviewer could not be reached";
            return v.searchedCount() + " listing(s) found, but none were checked — " + why
                    + ". They are shown as search results only; open each one and decide "
                    + "for yourself before saving it as a competitor.";
        }
        // When the model answered for only some of them, say so. Otherwise
        // "3 of 12 are this product" reads as a verdict on all twelve, when nine
        // were never looked at — and the reader has no way to tell the
        // difference between "not your product" and "not checked".
        int judged = v.matches().size() + v.rejected().size();
        String remainder = judged < v.searchedCount()
                ? " The other " + (v.searchedCount() - judged)
                        + " were not checked — review those yourself."
                : "";

        if (v.matches().isEmpty()) {
            return judged + " listing(s) checked, none of them your product. The reasons "
                    + "below say why — usually a different model or an accessory." + remainder;
        }
        return v.matches().size() + " of " + judged + " checked listing(s) are this product."
                + remainder;
    }

    // ---------- the model ----------

    /**
     * Runs the model over the candidates.
     *
     * @return null when it judged, otherwise why it could not — distinguishing a
     *         provider that failed from one that was never configured, because
     *         only the first is worth retrying and only the second is silent
     *         everywhere else in the logs
     */
    private String apply(String reference, List<MatchedListing> candidates) {
        if (!aiGateway.isAvailable()) {
            log.warn("No AI provider configured — {} listing(s) left unjudged", candidates.size());
            candidates.forEach(this::applyRulesVerdict);
            return NOT_CONFIGURED;
        }
        int max = Math.max(1, aiProps.getJudgeMaxListings());
        List<MatchedListing> judged = candidates.size() > max
                ? candidates.subList(0, max) : candidates;
        // Sized to fill the lanes rather than to fill a batch: the wait is the
        // slowest batch, so thirty listings answer sooner as three tens than as
        // one thirty. Same requests' worth of work, a third of the waiting.
        int batchSize = Math.max(1, aiProps.judgeSplitSize(judged.size()));

        // Batched rather than sent as one request. One request for a hundred
        // listings works — measured, 6,569 tokens and 82 seconds — but a reply
        // that truncates loses every verdict in it, and the caller waits the
        // whole time for the first of them. A batch that fails costs only its
        // own listings, and the rest are still judged.
        List<List<MatchedListing>> batches = new java.util.ArrayList<>();
        for (int from = 0; from < judged.size(); from += batchSize) {
            batches.add(judged.subList(from, Math.min(from + batchSize, judged.size())));
        }

        long started = System.currentTimeMillis();
        // In parallel, because each batch is almost entirely waiting for the
        // model. Sequentially, 80 listings took about a minute; the work itself
        // is no larger, it was just queued behind itself.
        List<String> errors = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        if (batches.size() == 1) {
            judgeBatch(reference, batches.get(0), 0, errors);
        } else {
            int threads = Math.min(batches.size(), Math.max(1, aiProps.getJudgeConcurrency()));
            try (java.util.concurrent.ExecutorService pool =
                         java.util.concurrent.Executors.newFixedThreadPool(threads, r -> {
                             Thread t = new Thread(r, "listing-judge");
                             t.setDaemon(true);
                             return t;
                         })) {
                List<java.util.concurrent.Future<?>> running = new java.util.ArrayList<>();
                for (int i = 0; i < batches.size(); i++) {
                    final int index = i;
                    running.add(pool.submit(() ->
                            judgeBatch(reference, batches.get(index), index * batchSize, errors)));
                }
                for (java.util.concurrent.Future<?> f : running) {
                    try {
                        f.get();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Judging was interrupted", e);
                    } catch (java.util.concurrent.ExecutionException e) {
                        // judgeBatch swallows its own failures; anything here is
                        // a defect rather than a provider problem.
                        throw new IllegalStateException("Judging failed unexpectedly", e.getCause());
                    }
                }
            }
        }
        int failedBatches = errors.size();
        String firstError = errors.isEmpty() ? null : errors.get(0);
        if (batches.size() > 1) {
            log.info("Judged {} listing(s) in {} batch(es) of {} across {} thread(s) in {}ms{}",
                    judged.size(), batches.size(), batchSize,
                    Math.min(batches.size(), aiProps.getJudgeConcurrency()),
                    System.currentTimeMillis() - started,
                    failedBatches > 0 ? ", " + failedBatches + " failed" : "");
        }
        // Anything past the cap is marked unjudged rather than left blank.
        candidates.stream().filter(l -> l.getDecision() == null)
                .forEach(this::applyRulesVerdict);

        // Only a total failure is reported as one: if some batches answered,
        // those verdicts stand and saying "unavailable" would throw them away.
        return failedBatches == batches.size() ? "PROVIDER_ERROR: " + firstError : null;
    }

    /**
     * One batch, with its failure kept to itself.
     *
     * @param offset where this batch starts in the whole list, for the log line
     */
    private void judgeBatch(String reference, List<MatchedListing> batch, int offset,
                            List<String> errors) {
        try {
            applyAiVerdicts(reference, batch);
        } catch (RuntimeException e) {
            errors.add(e.getMessage());
            log.warn("AI judging failed for listings {}-{}: {}",
                    offset + 1, offset + batch.size(), e.getMessage());
            batch.forEach(this::applyRulesVerdict);
        }
    }

    private void applyAiVerdicts(String reference, List<MatchedListing> candidates) {
        StringBuilder user = new StringBuilder("REFERENCE PRODUCT\n").append(reference);
        user.append("\nCANDIDATES\n");
        for (int i = 0; i < candidates.size(); i++) {
            MatchedListing l = candidates.get(i);
            user.append(i + 1).append(". ").append(l.getTitle());
            if (l.getSeller() != null && !l.getSeller().isBlank()) {
                user.append(" [seller ").append(l.getSeller()).append(']');
            }
            if (l.getCondition() != null && !l.getCondition().isBlank()) {
                user.append(" [").append(l.getCondition()).append(']');
            }
            user.append('\n');
        }

        AiCompletion completion = aiGateway.complete(AiCompletionRequest.builder()
                .systemPrompt(SYSTEM_PROMPT)
                .userPrompt(user.toString())
                // Room for a verdict on every candidate. This model reasons
                // before answering and the reasoning counts against the same
                // budget — measured at 530-1000 tokens, varying run to run. At
                // 1600 the reasoning could leave too little for the answers, and
                // the model then returned valid JSON covering only the first few
                // listings: the same search judged 4 of 12 one minute and 3 of 12
                // the next. Billing is on tokens used, not on this cap, so the
                // headroom is free.
                .temperature(0.0).jsonOnly(true).maxTokens(aiProps.judgeMaxTokens())
                .build());

        Map<Integer, JsonNode> byId = new HashMap<>();
        try {
            objectMapper.readTree(completion.getContent()).path("results")
                    .forEach(node -> byId.put(node.path("id").asInt(-1), node));
        } catch (Exception e) {
            throw new AiException("Could not read the model's verdicts: " + e.getMessage(), e);
        }

        for (int i = 0; i < candidates.size(); i++) {
            JsonNode verdict = byId.get(i + 1);
            MatchedListing listing = candidates.get(i);
            if (verdict == null) {
                // The model skipped this one; a rules score beats no answer.
                applyRulesVerdict(listing);
                continue;
            }
            String decision = normalise(verdict.path("decision").asText("UNCERTAIN"));
            listing.setDecision(decision);
            listing.setScore(coherentScore(verdict, decision));
            listing.setConfidence(verdict.path("confidence").isNumber()
                    ? verdict.get("confidence").asDouble() : null);
            listing.setMatchedAttributes(strings(verdict.path("matchedAttributes")));
            List<com.priceintel.backend.dto.response.MatchConflict> details =
                    conflicts(verdict.path("conflicts"));
            listing.setConflictDetails(details);
            // The old field stays, derived from the new one, so nothing
            // rendering attribute names breaks while the UI catches up.
            listing.setConflicts(details.stream()
                    .map(com.priceintel.backend.dto.response.MatchConflict::getField).toList());
            listing.setMissingEvidence(strings(verdict.path("missingEvidence")));
            listing.setReason(truncate(verdict.path("reason").asText(null)));
            listing.setJudgedBy(completion.getModel());
            listing.setPromptVersion(PROMPT_VERSION);
        }
    }

    /**
     * Marks a listing the model never ruled on.
     *
     * <p>Not a fallback verdict — there is no comparison performed here at all.
     * Without the model nothing can tell a renewed unit from a new one, and
     * UNCERTAIN with no score says exactly that.</p>
     */
    private void applyRulesVerdict(MatchedListing listing) {
        listing.setDecision("UNCERTAIN");
        listing.setJudgedBy(RULES);
        listing.setReason("Not judged — no AI verdict was available for this search.");
        // Null, not 50. No similarity is computed on this path, so a number
        // would invite ranking on a value carrying no signal — and 50 read as a
        // real "50% match" beside titles that plainly were not.
        listing.setScore(null);
        // Empty, not ["AI verdict"]. missingEvidence is a per-listing gap the
        // model noticed; "there was no model" is a property of the whole
        // response, and status NOT_JUDGED already says it once instead of on
        // every row.
        listing.setMissingEvidence(List.of());
    }

    /**
     * Reads the conflicts a verdict reports, in either shape.
     *
     * <p>The prompt asks for objects carrying both values, but a model will
     * sometimes answer with the bare attribute names it used to. Both are
     * accepted: a conflict named without its values is still worth showing, and
     * discarding it because of its shape would lose real information.</p>
     */
    private List<com.priceintel.backend.dto.response.MatchConflict> conflicts(JsonNode node) {
        List<com.priceintel.backend.dto.response.MatchConflict> out = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return out;
        }
        for (JsonNode n : node) {
            if (n.isTextual()) {
                String field = n.asText(null);
                if (field != null && !field.isBlank()) {
                    out.add(com.priceintel.backend.dto.response.MatchConflict.builder()
                            .field(field).severity("SOFT").build());
                }
                continue;
            }
            String field = n.path("field").asText(null);
            if (field == null || field.isBlank()) {
                continue;
            }
            out.add(com.priceintel.backend.dto.response.MatchConflict.builder()
                    .field(field)
                    .productValue(text(n, "productValue"))
                    .candidateValue(text(n, "candidateValue"))
                    // The model is not asked to grade severity; the hard rules
                    // in MatchScoringService decide what blocks a match.
                    .severity("SOFT")
                    .build());
        }
        return out;
    }

    private String text(JsonNode node, String field) {
        String v = node.path(field).asText(null);
        return v == null || v.isBlank() ? null : v;
    }

    private List<String> strings(JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(n -> {
            String text = n.asText(null);
            if (text != null && !text.isBlank()) {
                values.add(text);
            }
        });
        return values;
    }

    private java.util.Comparator<MatchedListing> byScoreDescending() {
        return (a, b) -> Integer.compare(
                b.getScore() == null ? 0 : b.getScore(),
                a.getScore() == null ? 0 : a.getScore());
    }

    private String normalise(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase().replace(' ', '_');
        return switch (value) {
            case "MATCH", "EQUIVALENT", "NOT_MATCH", "UNCERTAIN" -> value;
            default -> "UNCERTAIN";
        };
    }

    /** Guards against a score that contradicts its own decision. */
    private Integer coherentScore(JsonNode node, String decision) {
        if (!node.path("score").isNumber()) {
            return null;
        }
        int score = Math.max(0, Math.min(100, node.get("score").asInt()));
        if ("NOT_MATCH".equals(decision) && score > 50) {
            return 100 - score;
        }
        if (("MATCH".equals(decision) || "EQUIVALENT".equals(decision)) && score < 50) {
            return 100 - score;
        }
        return score;
    }

    private void appendIf(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append(": ").append(value).append('\n');
        }
    }

    private String truncate(String s) {
        return s == null || s.length() <= 300 ? s : s.substring(0, 300);
    }
}
