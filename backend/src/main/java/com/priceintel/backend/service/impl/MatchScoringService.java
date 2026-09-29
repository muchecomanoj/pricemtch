package com.priceintel.backend.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.priceintel.backend.dto.response.MatchSignal;
import com.priceintel.backend.entity.CompetitorListing;
import com.priceintel.backend.entity.Product;
import com.priceintel.backend.entity.ProductAttribute;
import com.priceintel.backend.entity.ProductIdentifier;

import lombok.Value;

/**
 * Deterministic match-scoring engine (FR-MATCH). Compares a candidate
 * competitor listing to the product it was found for and produces:
 *   - a confidence score 0-100
 *   - a list of {@link MatchSignal}s explaining the score (the chips on the card)
 *
 * <p>No randomness and no external calls — the same inputs always yield the
 * same score, so results are reproducible and testable.</p>
 */
@Service
public class MatchScoringService {

    // Point weights for each signal (tuned so a solid identifier match alone clears "likely").
    private static final int W_IDENTIFIER = 45;
    private static final int W_BRAND = 15;
    private static final int W_MODEL = 20;
    private static final int W_TITLE_MAX = 40;   // scaled by title similarity 0..1
    private static final int P_CONDITION = 10;   // penalty: used/refurbished
    private static final int P_PACK = 8;         // penalty: pack size likely differs

    /**
     * Patterns that state how many units a listing contains: "2 pack",
     * "pack of 3", "4-count", "6pk", "twin pack".
     */
    private static final java.util.regex.Pattern PACK_PATTERN = java.util.regex.Pattern.compile(
            "(?:pack\\s*of\\s*(\\d{1,3}))"
          + "|(?:(\\d{1,3})\\s*[-\\s]?(?:pack|pk|count|ct|pcs|pieces))",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    @Value
    public static class MatchAssessment {
        int score;
        List<MatchSignal> signals;

        /**
         * A conflict that disqualifies this listing as a comparable, whatever
         * the score says. Null when the listing is comparable.
         *
         * <p>FR-MATCH-001: an exact ASIN or GTIN match is a strong signal but
         * does not override pack-size and condition checks. A 2-pack is simply
         * not the same offer as a single unit, so comparing their prices —
         * or repricing against one — is wrong however confident the match
         * looks.</p>
         */
        String blockedReason;

        public boolean isBlocked() {
            return blockedReason != null;
        }
    }

    public MatchAssessment assess(Product product, CompetitorListing listing) {
        List<MatchSignal> signals = new ArrayList<>();
        double score = 0;

        String listingTitle = norm(listing.getTitle());
        Set<String> listingTokens = tokens(listing.getTitle());

        // 1) Identifier exact match (ASIN/UPC/EAN/GTIN/MPN) — strongest signal.
        String matchedIdType = identifierMatch(product, listing, listingTitle);
        if (matchedIdType != null) {
            score += W_IDENTIFIER;
            signals.add(sig("IDENTIFIER_MATCH", matchedIdType + " exact match", true));
        }

        // 2) Brand present in the listing title.
        boolean brandMatch = product.getBrand() != null
                && !product.getBrand().isBlank()
                && containsAll(listingTokens, tokens(product.getBrand()));
        if (brandMatch) {
            score += W_BRAND;
        }

        // 3) Model (from any attribute row) present in the listing title.
        boolean modelMatch = false;
        for (ProductAttribute a : safe(product.getAttributes())) {
            if (a.getModel() != null && !a.getModel().isBlank()
                    && containsAll(listingTokens, tokens(a.getModel()))) {
                modelMatch = true;
                break;
            }
        }
        if (modelMatch) {
            score += W_MODEL;
        }
        if (brandMatch && modelMatch) {
            signals.add(sig("BRAND_MODEL", "Brand + model match", true));
        } else if (brandMatch) {
            signals.add(sig("BRAND_MODEL", "Brand match", true));
        } else if (modelMatch) {
            signals.add(sig("BRAND_MODEL", "Model match", true));
        }

        // 4) Title similarity (Jaccard token overlap) — always shown.
        double sim = jaccard(tokens(product.getTitle()), listingTokens);
        score += W_TITLE_MAX * sim;
        signals.add(sig("TITLE_SIMILARITY", "Title similarity " + fmt(sim), sim >= 0.4));

        // 5) Condition warning — used/refurbished listings are weaker comparables.
        String cond = listing.getCondition();
        if (cond != null && !cond.isBlank()
                && !cond.equalsIgnoreCase("NEW") && !cond.equalsIgnoreCase("NEW_OTHER")) {
            score -= P_CONDITION;
            signals.add(sig("CONDITION", "Condition: " + prettyCondition(cond), false));
        }

        // 6) Pack size — a HARD rule, not a penalty.
        //
        // Reading the competitor's own pack size out of its title lets this
        // catch the case the previous version could not: our product is a
        // single unit and the listing is a 2-pack. That was invisible before,
        // because the old check only ran when OUR pack quantity was above one.
        int ourPack = product.getPackQuantity() != null ? product.getPackQuantity() : 1;
        Integer theirPack = packSizeOf(listing.getTitle());
        String blocked = null;

        if (theirPack != null && theirPack != ourPack) {
            blocked = "Pack size differs — ours is " + describePack(ourPack)
                    + ", this listing is " + describePack(theirPack)
                    + ". Prices are not comparable.";
            signals.add(sig("PACK_SIZE", "Pack size conflict: " + ourPack + " vs " + theirPack, false));
        } else if (ourPack > 1 && theirPack == null) {
            // Ours is a multipack and the listing says nothing about quantity:
            // suspicious but not provable, so it stays a penalty.
            score -= P_PACK;
            signals.add(sig("PACK_SIZE", "Pack size unconfirmed on a multipack", false));
        }

        // 7) Condition — also a hard rule where the two differ.
        // A used unit is a different offer from a new one; the FRD asks for
        // renewed, used and new to be separate comparable groups.
        String ourCondition = product.getCondition() != null
                ? product.getCondition().name() : "NEW";
        String theirCondition = normaliseCondition(listing.getCondition());
        if (blocked == null && theirCondition != null && !theirCondition.equals(ourCondition)) {
            blocked = "Condition differs — ours is " + ourCondition.toLowerCase()
                    + ", this listing is " + theirCondition.toLowerCase()
                    + ". Prices are not comparable.";
        }

        int clamped = (int) Math.round(Math.max(0, Math.min(100, score)));
        return new MatchAssessment(clamped, signals, blocked);
    }

    /**
     * The pack size a listing states, or null when it says nothing.
     *
     * <p>Null means "not stated", which is different from "one". Treating an
     * unstated quantity as a single unit would block honest matches on the
     * majority of listings, which simply do not mention packaging.</p>
     */
    public Integer packSizeOf(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        // norm() strips spaces, so "Twin Pack" arrives as "twinpack".
        String flattened = norm(title);
        if (flattened.contains("twinpack") || flattened.contains("packof2")) {
            return 2;
        }
        java.util.regex.Matcher m = PACK_PATTERN.matcher(title);
        if (m.find()) {
            String value = m.group(1) != null ? m.group(1) : m.group(2);
            try {
                int parsed = Integer.parseInt(value);
                // Guard against catching a model number or capacity.
                return parsed > 0 && parsed <= 500 ? parsed : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** Marketplace condition strings mapped onto our own vocabulary. */
    private String normaliseCondition(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String c = raw.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        if (c.startsWith("NEW")) {
            return "NEW";
        }
        if (c.contains("REFURB") || c.contains("RENEW")) {
            return "REFURBISHED";
        }
        if (c.contains("USED") || c.contains("PRE_OWNED") || c.contains("OPEN_BOX")) {
            return "USED";
        }
        return null; // unrecognised: do not block on a string we cannot read
    }

    private String describePack(int qty) {
        return qty == 1 ? "a single unit" : "a " + qty + "-pack";
    }

    // ---------- helpers ----------

    /** Returns the identifier type name that matched (e.g. "ASIN"), or null. */
    private String identifierMatch(Product product, CompetitorListing listing, String listingTitle) {
        String itemId = norm(listing.getMarketplaceItemId());
        for (ProductIdentifier id : safe(product.getIdentifiers())) {
            String val = id.getNormalizedValue() != null && !id.getNormalizedValue().isBlank()
                    ? norm(id.getNormalizedValue()) : norm(id.getOriginalValue());
            if (val == null || val.isBlank()) {
                continue;
            }
            // Match if the marketplace item id equals the identifier (Amazon ASIN case),
            // or the identifier appears verbatim in the listing title.
            if (val.equals(itemId) || (listingTitle != null && listingTitle.contains(val))) {
                return id.getType() != null ? id.getType().name() : "ID";
            }
        }
        return null;
    }

    private MatchSignal sig(String code, String label, boolean positive) {
        return MatchSignal.builder().code(code).label(label).positive(positive).build();
    }

    private String prettyCondition(String c) {
        String s = c.trim().toLowerCase().replace('_', ' ');
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    /** lower-case, strip everything except letters/digits (for exact id containment). */
    private String norm(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    /** split into a set of alphanumeric word tokens (length >= 2). */
    private Set<String> tokens(String s) {
        Set<String> out = new HashSet<>();
        if (s == null) {
            return out;
        }
        for (String t : Arrays.asList(s.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim().split("\\s+"))) {
            if (t.length() >= 2) {
                out.add(t);
            }
        }
        return out;
    }

    private boolean containsAll(Set<String> haystack, Set<String> needles) {
        return !needles.isEmpty() && haystack.containsAll(needles);
    }

    private double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) inter.size() / union.size();
    }

    private String fmt(double d) {
        return String.format(java.util.Locale.US, "%.2f", d);
    }

    private <T> List<T> safe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
