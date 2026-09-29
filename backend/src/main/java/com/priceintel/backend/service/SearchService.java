package com.priceintel.backend.service;

import com.priceintel.backend.dto.request.SearchRequest;
import com.priceintel.backend.dto.response.PagedResponse;
import com.priceintel.backend.dto.response.SearchHistoryResponse;
import com.priceintel.backend.dto.response.SearchResponse;

/**
 * Product search over the local database (Phase 5). Runs a priority waterfall
 * and records every search in the history.
 */
public interface SearchService {

    SearchResponse search(SearchRequest request);

    PagedResponse<SearchHistoryResponse> getHistory(int page, int size);
}
