package com.priceintel.backend.ai;

/**
 * Provider-neutral AI gateway (the seam that lets us swap OpenAI, Claude, etc.
 * without touching business code). Phase 9 ships an OpenAI implementation.
 */
public interface AiGateway {

    /** Whether a real provider is configured and ready to call. */
    boolean isAvailable();

    /** Runs a completion and returns the content plus token usage. */
    AiCompletion complete(AiCompletionRequest request);
}
