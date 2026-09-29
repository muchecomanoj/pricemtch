package com.priceintel.backend.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The three "what just happened" lists at the bottom of the Dashboard. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardActivities {

    private List<SearchItem> searches;
    private List<AlertItem> alerts;
    private List<RecommendationItem> recommendations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchItem {
        private Long id;
        private String query;
        /** What the search matched on: ASIN, UPC, TITLE… */
        private String type;
        private Integer results;
        /** Ready to print, e.g. "2 min ago". */
        private String time;
        /** The same moment as a timestamp, for anyone who wants to format it differently. */
        private Instant at;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlertItem {
        private Long id;
        /** Readable rule name, e.g. "Price Drop". */
        private String type;
        private String product;
        /** Badge colour: danger, warning or info. */
        private String severity;
        private String time;
        private Instant at;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendationItem {
        private Long id;
        private String product;
        private BigDecimal price;
        /** 0 to 1, as the screen multiplies it by 100. */
        private BigDecimal confidence;
        /** Low, Medium or High — how sure the suggestion is. */
        private String risk;
    }
}
