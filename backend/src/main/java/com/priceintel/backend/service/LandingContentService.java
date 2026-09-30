package com.priceintel.backend.service;

import java.util.List;
import java.util.Map;

import com.priceintel.backend.constants.LandingSection;
import com.priceintel.backend.dto.request.LandingContentUpdateRequest;
import com.priceintel.backend.dto.response.LandingContentResponse;

/** The editable copy on the public landing page. */
public interface LandingContentService {

    /**
     * Every visible section, keyed by section name, with each payload already
     * parsed so the browser receives real JSON rather than a string to parse
     * again. Hidden sections are left out.
     */
    Map<String, Object> publicContent();

    /** One section's parsed payload, or null when it is missing or hidden. */
    Object publicSection(LandingSection section);

    /** Every section including hidden ones, for the Super Admin's editor. */
    List<LandingContentResponse> listAll();

    /** Saves one section. Rejects a payload that is not a JSON object. */
    LandingContentResponse update(LandingSection section, LandingContentUpdateRequest request);
}
