package com.priceintel.backend.ai;

import com.priceintel.backend.constants.AiProvider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Provider-neutral AI response: the content plus token usage and model info,
 * so callers can persist governance data regardless of provider.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiCompletion {
    private String content;
    private AiProvider provider;
    private String model;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
}
