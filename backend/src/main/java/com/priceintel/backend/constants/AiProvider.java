package com.priceintel.backend.constants;

/**
 * Which engine produced a match result. DETERMINISTIC is the built-in rules
 * engine used when no AI provider is configured.
 */
public enum AiProvider {
    OPENAI,

    /** Open-weight models on Groq's hardware, via an OpenAI-compatible endpoint. */
    GROQ,

    /** A model running on our own machine, again OpenAI-compatible. */
    OLLAMA,

    DETERMINISTIC;

    /**
     * Identifies the provider from the endpoint it is pointed at.
     *
     * <p>All three speak the same protocol, so the gateway cannot tell them
     * apart from the traffic — and recording every call as OPENAI would make the
     * audit log lie, which matters when comparing one model's verdicts against
     * another's after a switch.</p>
     */
    public static AiProvider fromBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return OPENAI;
        }
        String url = baseUrl.toLowerCase();
        if (url.contains("groq.com")) {
            return GROQ;
        }
        if (url.contains("localhost") || url.contains("127.0.0.1") || url.contains("ollama")) {
            return OLLAMA;
        }
        return OPENAI;
    }
}
