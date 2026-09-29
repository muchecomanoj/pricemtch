package com.priceintel.backend.dto.response;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The AI Analyst's answer. Deterministic — every figure comes from stored data
 * (no LLM). {@code answer} is display text; {@code items} is optional structured
 * evidence the frontend can render as a table.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalystResponse {
    private String question;
    private String intent;       // LOWEST_MARGIN, MARGIN_BREACH, PRICE_RECOMMENDATION, ...
    private String answer;       // human-readable summary
    private List<Item> items;    // supporting rows (optional)
    private String source;       // e.g. "Computed from stored costs, prices and competitor listings"
    private Instant generatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private Long productId;
        private String product;
        private String metric;
        private String value;
        private String note;
    }
}
