package com.priceintel.backend.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Provider-neutral request to the AI Gateway. Business services build this and
 * never talk to a specific provider's SDK directly.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiCompletionRequest {
    private String systemPrompt;
    private String userPrompt;
    private Double temperature;
    private Integer maxTokens;
    /** When true, the provider is asked to return a JSON object only. */
    private boolean jsonOnly;

    /**
     * Overrides the configured model for this one call.
     *
     * <p>Different jobs need different engines: judging two titles wants a small
     * fast model, while finding a product on the web needs one that can search.
     * Pinning a single model globally would force the expensive one on every
     * comparison.</p>
     */
    private String model;

    /**
     * An image for the model to look at, as a data URI or an http(s) URL.
     *
     * <p>Only some models accept one — on this account, {@code qwen/qwen3.6-27b}
     * does and the others answer HTTP 400. Set the model explicitly when using
     * this rather than relying on the configured default.</p>
     */
    private String imageUrl;
}
