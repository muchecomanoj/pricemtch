package com.priceintel.backend.dto.request;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Inputs to the price-recommendation engine (all optional). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationRequest {
    /** Desired contribution margin % (default 20). */
    private BigDecimal targetMarginPct;
    /** Hard floor — never recommend below this. */
    private BigDecimal floorPrice;
    /** Hard ceiling — never recommend above this. */
    private BigDecimal ceilingPrice;
}
