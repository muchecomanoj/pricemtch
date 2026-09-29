package com.priceintel.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenUsage {
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
}
