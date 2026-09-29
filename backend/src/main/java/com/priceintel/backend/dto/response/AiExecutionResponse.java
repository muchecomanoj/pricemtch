package com.priceintel.backend.dto.response;

import java.time.LocalDateTime;

import com.priceintel.backend.constants.AiProvider;
import com.priceintel.backend.constants.MatchDecision;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiExecutionResponse {
    private Long id;
    private String task;
    private AiProvider provider;
    private String model;
    private String promptVersion;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Long latencyMs;
    private String schemaStatus;
    private MatchDecision decision;
    private Integer score;
    private boolean success;
    private String createdBy;
    private LocalDateTime createdAt;
}
