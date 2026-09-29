package com.priceintel.backend.dto.response;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A scheduled competitor check, and how its last run went. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitorResponse {

    private Long id;
    private Long productId;
    private String productTitle;
    private List<String> markets;
    private Integer maxResults;
    private Integer intervalMinutes;
    private boolean enabled;

    private Instant lastRunAt;
    private Instant nextRunAt;
    private Integer lastResultCount;

    /** COMPLETED or FAILED — lets the UI show a monitor that is silently broken. */
    private String lastStatus;
    private String lastMessage;

    /**
     * The fastest cadence this tenant's plan allows, so the UI can cap its
     * frequency picker and explain the limit rather than let the user pick a
     * value the server will reject.
     */
    private Integer minIntervalMinutes;
}
