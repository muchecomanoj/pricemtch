package com.priceintel.backend.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceintel.backend.constants.LandingSection;
import com.priceintel.backend.dto.request.LandingContentUpdateRequest;
import com.priceintel.backend.dto.response.LandingContentResponse;
import com.priceintel.backend.entity.LandingContent;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.exception.ResourceNotFoundException;
import com.priceintel.backend.repository.LandingContentRepository;
import com.priceintel.backend.service.LandingContentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reads and edits the landing page's copy.
 *
 * <p>A section whose stored JSON will not parse is skipped rather than thrown:
 * one bad edit should cost the site one section, not the whole page. Edits are
 * validated on the way in, so that can only happen to content written straight
 * into the database.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LandingContentServiceImpl implements LandingContentService {

    private final LandingContentRepository repository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> publicContent() {
        Map<String, Object> content = new LinkedHashMap<>();
        List<LandingContent> sections = new ArrayList<>(repository.findByActiveTrue());
        // Ordered by the enum, so the response always lists sections in the order
        // they appear down the page rather than in whatever order they were saved.
        sections.sort(Comparator.comparing(LandingContent::getSection));

        for (LandingContent section : sections) {
            Object parsed = parseOrNull(section);
            if (parsed != null) {
                content.put(section.getSection().name(), parsed);
            }
        }
        return content;
    }

    @Override
    @Transactional(readOnly = true)
    public Object publicSection(LandingSection section) {
        return repository.findBySection(section)
                .filter(LandingContent::isActive)
                .map(this::parseOrNull)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LandingContentResponse> listAll() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(LandingContent::getSection))
                .map(LandingContentResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public LandingContentResponse update(LandingSection section, LandingContentUpdateRequest request) {
        JsonNode parsed;
        try {
            parsed = objectMapper.readTree(request.getPayload());
        } catch (JsonProcessingException e) {
            throw new BadRequestException("That content is not valid JSON: " + e.getOriginalMessage());
        }
        if (!parsed.isObject()) {
            throw new BadRequestException("Section content must be a JSON object, for example {\"title\": \"...\"}.");
        }

        LandingContent content = repository.findBySection(section)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No landing section called " + section.name() + "."));

        content.setPayload(request.getPayload());
        if (request.getActive() != null) {
            content.setActive(request.getActive());
        }
        return LandingContentResponse.from(repository.save(content));
    }

    private Object parseOrNull(LandingContent content) {
        try {
            return objectMapper.readValue(content.getPayload(), Object.class);
        } catch (JsonProcessingException e) {
            log.warn("Landing section {} holds invalid JSON and was left out of the page: {}",
                    content.getSection(), e.getOriginalMessage());
            return null;
        }
    }
}
