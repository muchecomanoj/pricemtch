package com.priceintel.backend.dto.response;

import java.time.LocalDateTime;

import com.priceintel.backend.constants.SearchStage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchHistoryResponse {

    private Long id;
    private String query;
    private SearchStage matchedStage;
    private int matchCount;
    private String searchedBy;
    private LocalDateTime createdAt;
}
