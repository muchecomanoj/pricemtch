package com.priceintel.backend.dto.response;

import com.priceintel.backend.constants.SearchStage;
import com.priceintel.backend.constants.SearchStepStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One line of the search trace: which stage ran, what value it used, the
 * outcome, and how many matches it produced.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchTraceStep {

    private SearchStage stage;
    private String queryValue;
    private SearchStepStatus status;
    private int matchCount;
    private String note;
}
