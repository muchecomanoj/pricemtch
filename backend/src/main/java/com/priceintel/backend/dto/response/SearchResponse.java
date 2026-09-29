package com.priceintel.backend.dto.response;

import java.util.List;

import com.priceintel.backend.constants.SearchStage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The result of a search: which stage matched, the full trace, and the matching
 * product details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse {

    private String query;
    private SearchStage matchedStage;
    private int totalMatches;
    private List<SearchTraceStep> trace;
    private List<ProductResponse> results;
}
