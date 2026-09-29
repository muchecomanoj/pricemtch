package com.priceintel.backend.service.impl;


import org.springframework.stereotype.Service;

import com.priceintel.backend.ai.AiCompletion;
import com.priceintel.backend.ai.AiCompletionRequest;
import com.priceintel.backend.ai.AiGateway;
import com.priceintel.backend.ai.OpenAiProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns a photograph into words a marketplace can be searched with.
 *
 * <p>No marketplace accepts an image, so this is the only way one can lead
 * anywhere: the model looks at the picture and writes what it sees, and those
 * words go to the marketplace's own keyword search. The model never names a
 * price or an identifier — it describes.</p>
 *
 * <p>The result is a starting point, not an answer. A photograph of a clearly
 * branded box gives a usable query; a photograph of an unbranded object gives
 * "black digital wristwatch", which matches thousands of listings. Whatever
 * comes back still has to be judged, and then chosen by a person.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageDescriptionService {

    /**
     * Asks for a brand when the design is recognisable, not only when it is
     * printed.
     *
     * <p>Short on purpose, and the shortness was earned. A version forbidding an
     * unprinted brand produced "white wireless earbuds with silicone tips" for a
     * photograph of AirPods — careful, and useless as a search. A version
     * pressing for the exact model sent this reasoning model into six thousand
     * characters of deliberation over Pro versus Pro 2 and never reached an
     * answer at all. Two plain sentences produce "Apple AirPods Pro Wireless
     * Earbuds" in a third of the tokens.</p>
     *
     * <p>Naming a likely brand is safe because nothing here is trusted: the
     * words only choose what the marketplace searches for, every listing that
     * comes back is judged separately, and a wrong guess costs a search rather
     * than a wrong answer.</p>
     */
    private static final String PROMPT =
            "Describe this product for an Amazon search. Reply with ONLY the search "
            + "words, at most 12 words. Include the brand if you recognise it.";

    private final AiGateway aiGateway;
    private final OpenAiProperties props;

    public boolean isAvailable() {
        return props.isVisionAvailable() && aiGateway.isAvailable();
    }

    /**
     * @return search words for the image, or empty when vision is unavailable or
     *         the model could not describe it
     */
    public String describe(String imageUrl) {
        if (!isAvailable() || imageUrl == null || imageUrl.isBlank()) {
            return "";
        }
        try {
            AiCompletion completion = aiGateway.complete(AiCompletionRequest.builder()
                    .userPrompt(PROMPT)
                    .imageUrl(imageUrl)
                    .model(props.getVisionModel())
                    .temperature(0.0)
                    // Generous, because this model reasons at length before
                    // answering and the answer comes last. At 500 the working
                    // was truncated mid-thought and there was no answer to
                    // keep — a correct description lost to an arbitrary cap.
                    // The reply is stripped to a few words either way, so the
                    // budget costs nothing when it is not needed.
                    .maxTokens(1500)
                    .build());
            String words = clean(completion.getContent());
            log.info("Image described as '{}'", words);
            return words;
        } catch (RuntimeException e) {
            log.warn("Image description failed: {}", e.getMessage());
            return "";
        }
    }

    /** Strips the model's working and any leftover punctuation or quoting. */
    private String clean(String content) {
        if (content == null) {
            return "";
        }
        // Take what follows the last </think> rather than cutting the block out.
        // A reply can open a second thought after answering, and removing each
        // block in turn then leaves a stray tag that looks like truncation.
        int end = content.lastIndexOf("</think>");
        String text;
        if (end >= 0) {
            text = content.substring(end + "</think>".length()).trim();
        } else if (content.contains("<think>")) {
            // Opened and never closed: the budget ran out mid-thought and the
            // answer was never written. Everything present is working-out.
            log.warn("Vision reply was cut off mid-reasoning ({} chars) — no description. Tail: {}",
                    content.length(), tail(content));
            return "";
        } else {
            text = content.trim();
        }
        if (text.isBlank()) {
            log.warn("Vision reply had no answer after its reasoning. Tail: {}", tail(content));
            return "";
        }
        text = text.replaceAll("^[\"'`]+|[\"'`.]+$", "").trim();
        // A model that explains itself despite the instruction usually does so
        // after a newline; the first line is the answer.
        int newline = text.indexOf('\n');
        if (newline > 0) {
            text = text.substring(0, newline).trim();
        }
        return text.length() > 200 ? text.substring(0, 200) : text;
    }
    /** The last of a reply, for a log line that has to explain a failure. */
    private String tail(String content) {
        String flat = content.replaceAll("\\s+", " ").trim();
        return flat.length() <= 160 ? flat : "…" + flat.substring(flat.length() - 160);
    }
}
