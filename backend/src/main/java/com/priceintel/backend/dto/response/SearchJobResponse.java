package com.priceintel.backend.dto.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchJobResponse {
    private Long id;
    private Long productId;
    private String markets;
    private String status;
    private int resultCount;
    private String correlationId;
    private Instant finishedAt;
    private String note;
}
