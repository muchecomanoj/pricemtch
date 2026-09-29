package com.priceintel.backend.dto.request;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Options for generating a competitor-price report (Reports page). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportRequest {
    /** CSV or JSON. Defaults to CSV. */
    private String format;
    /** Marketplace filter (AMAZON/EBAY/WEB) or null/"All". */
    private String marketplace;
    /** Category name filter or null/"All". */
    private String category;
    private LocalDate from;
    private LocalDate to;
}
