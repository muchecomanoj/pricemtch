package com.priceintel.backend.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.priceintel.backend.dto.response.MatchConflict;

import lombok.Getter;

/**
 * Deterministic product comparison. Scores brand, title, model, color, variant,
 * and pack quantity, and detects conflicts / missing evidence. This is the
 * ground-truth signal the AI explanation must respect (per the FRD, rules are
 * authoritative on conflicts; AI explains).
 */
public final class MatchComparator {

    private MatchComparator() {
    }

    // Field weights (sum = 100). Image is not scored here (needs vision).
    private static final int W_BRAND = 20;
    private static final int W_TITLE = 25;
    private static final int W_MODEL = 20;
    private static final int W_COLOR = 10;
    private static final int W_VARIANT = 10;
    private static final int W_PACK = 15;

    @Getter
    public static class Comparison {
        private int score;
        private double coverage;
        private boolean hardConflict;
        private final List<MatchConflict> conflicts = new ArrayList<>();
        private final List<String> matchedAttributes = new ArrayList<>();
        private final List<String> missingEvidence = new ArrayList<>();
    }

    public static Comparison compare(ComparableProduct a, ComparableProduct b) {
        Comparison c = new Comparison();
        double earned = 0;
        int presentBothCount = 0;
        int totalFields = 6;

        // brand
        presentBothCount += scoreText("brand", a.getBrand(), b.getBrand(), W_BRAND, true, c) ? 1 : 0;
        // model
        presentBothCount += scoreText("model", a.getModel(), b.getModel(), W_MODEL, true, c) ? 1 : 0;
        // color
        presentBothCount += scoreText("color", a.getColor(), b.getColor(), W_COLOR, false, c) ? 1 : 0;
        // variant
        presentBothCount += scoreText("variant", a.getVariant(), b.getVariant(), W_VARIANT, false, c) ? 1 : 0;

        // recompute earned from matched (scoreText adds to matched but not earned) -> compute below
        earned += matchedWeight(c);

        // title (fuzzy)
        if (present(a.getTitle()) && present(b.getTitle())) {
            presentBothCount++;
            double sim = titleSimilarity(a.getTitle(), b.getTitle());
            earned += sim * W_TITLE;
            if (sim >= 0.6) {
                c.matchedAttributes.add("title");
            } else if (sim < 0.3) {
                c.conflicts.add(conflict("title", a.getTitle(), b.getTitle(), "SOFT"));
            }
        } else {
            c.missingEvidence.add("title");
        }

        // packQuantity (exact; a mismatch is a HARD conflict)
        if (a.getPackQuantity() != null && b.getPackQuantity() != null) {
            presentBothCount++;
            if (a.getPackQuantity().equals(b.getPackQuantity())) {
                earned += W_PACK;
                c.matchedAttributes.add("packQuantity");
            } else {
                c.hardConflict = true;
                c.conflicts.add(conflict("packQuantity",
                        String.valueOf(a.getPackQuantity()), String.valueOf(b.getPackQuantity()), "HARD"));
            }
        } else {
            c.missingEvidence.add("packQuantity");
        }

        // image (cannot be visually compared without vision)
        if (present(a.getImageUrl()) && present(b.getImageUrl())) {
            c.missingEvidence.add("image (visual comparison requires vision model)");
        } else {
            c.missingEvidence.add("image");
        }

        c.score = (int) Math.round(Math.min(earned, 100));
        c.coverage = (double) presentBothCount / totalFields;
        return c;
    }

    // Scores an exact-match text field; returns true if both values present.
    private static boolean scoreText(String field, String av, String bv, int weight,
                                     boolean hardOnConflict, Comparison c) {
        if (present(av) && present(bv)) {
            if (normalize(av).equals(normalize(bv))) {
                c.matchedAttributes.add(field);
            } else {
                if (hardOnConflict) {
                    c.hardConflict = true;
                }
                c.conflicts.add(conflict(field, av, bv, hardOnConflict ? "HARD" : "SOFT"));
            }
            return true;
        }
        c.missingEvidence.add(field);
        return false;
    }

    private static double matchedWeight(Comparison c) {
        double w = 0;
        for (String f : c.matchedAttributes) {
            w += switch (f) {
                case "brand" -> W_BRAND;
                case "model" -> W_MODEL;
                case "color" -> W_COLOR;
                case "variant" -> W_VARIANT;
                default -> 0;
            };
        }
        return w;
    }

    private static double titleSimilarity(String a, String b) {
        Set<String> ta = tokens(a);
        Set<String> tb = tokens(b);
        if (ta.isEmpty() || tb.isEmpty()) {
            return 0;
        }
        Set<String> inter = new HashSet<>(ta);
        inter.retainAll(tb);
        Set<String> union = new HashSet<>(ta);
        union.addAll(tb);
        return (double) inter.size() / union.size();
    }

    private static Set<String> tokens(String s) {
        Set<String> set = new HashSet<>();
        for (String t : normalize(s).split("\\s+")) {
            if (t.length() > 1) {
                set.add(t);
            }
        }
        return set;
    }

    private static MatchConflict conflict(String field, String pv, String cv, String severity) {
        return MatchConflict.builder()
                .field(field).productValue(pv).candidateValue(cv).severity(severity).build();
    }

    private static boolean present(String s) {
        return s != null && !s.isBlank();
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase().replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
    }

    // exposed for tests / potential reuse
    public static List<String> attributeOrder() {
        return Arrays.asList("brand", "title", "model", "color", "variant", "packQuantity", "image");
    }
}
