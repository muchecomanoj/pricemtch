package com.priceintel.backend.service;

import com.priceintel.backend.dto.request.MatchRequest;
import com.priceintel.backend.dto.response.MatchResponse;
import com.priceintel.backend.dto.response.PagedResponse;
import com.priceintel.backend.dto.response.AiExecutionResponse;

/**
 * AI product matching use cases (Phase 9).
 */
public interface MatchService {

    MatchResponse match(MatchRequest request);

    PagedResponse<AiExecutionResponse> getExecutions(int page, int size);
}
