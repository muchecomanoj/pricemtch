package com.priceintel.backend.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceintel.backend.ai.AiCompletion;
import com.priceintel.backend.ai.AiCompletionRequest;
import com.priceintel.backend.ai.AiGateway;
import com.priceintel.backend.ai.ComparableProduct;
import com.priceintel.backend.ai.MatchComparator;
import com.priceintel.backend.constants.AiProvider;
import com.priceintel.backend.constants.MatchDecision;
import com.priceintel.backend.dto.request.MatchCandidate;
import com.priceintel.backend.dto.request.MatchRequest;
import com.priceintel.backend.dto.response.AiExecutionResponse;
import com.priceintel.backend.dto.response.MatchConflict;
import com.priceintel.backend.dto.response.MatchResponse;
import com.priceintel.backend.dto.response.PagedResponse;
import com.priceintel.backend.dto.response.TokenUsage;
import com.priceintel.backend.entity.AiExecution;
import com.priceintel.backend.entity.Product;
import com.priceintel.backend.entity.ProductAttribute;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.exception.ResourceNotFoundException;
import com.priceintel.backend.mapper.AiMapper;
import com.priceintel.backend.repository.AiExecutionRepository;
import com.priceintel.backend.repository.ProductRepository;
import com.priceintel.backend.security.TenantContext;
import com.priceintel.backend.service.MatchService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchServiceImpl implements MatchService {

    private final ProductRepository productRepository;
    private final AiExecutionRepository executionRepository;
    private final AiGateway aiGateway;
    private final AiMapper aiMapper;
    private final ObjectMapper objectMapper;

    private static final String PROMPT_VERSION = "match-v1";
    private static final String TASK = "PRODUCT_MATCH";

    @Override
    @Transactional
    public MatchResponse match(MatchRequest request) {
        if ((request.getCandidateId() == null) == (request.getCandidate() == null)) {
            throw new BadRequestException("Provide exactly one of candidateId or candidate");
        }

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + request.getProductId()));
        ComparableProduct a = fromProduct(product);
        ComparableProduct b = resolveCandidate(request);

        MatchComparator.Comparison cmp = MatchComparator.compare(a, b);

        long start = System.currentTimeMillis();
        MatchResponse response;
        AiProvider provider;
        String model;
        String schemaStatus = "VALID";
        boolean success = true;
        String message = null;
        Integer pt = null, ct = null, tt = null;

        if (aiGateway.isAvailable()) {
            provider = AiProvider.OPENAI;
            try {
                AiCompletion completion = aiGateway.complete(buildAiRequest(a, b, cmp));
                model = completion.getModel();
                pt = completion.getPromptTokens();
                ct = completion.getCompletionTokens();
                tt = completion.getTotalTokens();
                response = parseAiResponse(completion.getContent(), cmp);
                response.setTokenUsage(new TokenUsage(pt, ct, tt));
            } catch (Exception e) {
                // Fall back to deterministic result but record the failure.
                log.warn("AI match failed, falling back to deterministic: {}", e.getMessage());
                schemaStatus = "INVALID";
                success = false;
                message = "AI error: " + e.getMessage();
                model = "fallback";
                response = deterministicResponse(cmp);
                response.setProvider(AiProvider.OPENAI);
            }
        } else {
            provider = AiProvider.DETERMINISTIC;
            model = "rules-1";
            response = deterministicResponse(cmp);
        }

        long latency = System.currentTimeMillis() - start;
        response.setProvider(provider);
        response.setModel(model);
        response.setPromptVersion(PROMPT_VERSION);

        AiExecution execution = executionRepository.save(AiExecution.builder()
                .tenantId(TenantContext.getTenantId())
                .task(TASK).provider(provider).model(model).promptVersion(PROMPT_VERSION)
                .promptTokens(pt).completionTokens(ct).totalTokens(tt)
                .latencyMs(latency).schemaStatus(schemaStatus)
                .decision(response.getDecision()).score(response.getScore())
                .success(success).message(message)
                .build());

        response.setExecutionId(execution.getId());
        log.info("Match product {} vs candidate -> {} (score {}) via {}",
                request.getProductId(), response.getDecision(), response.getScore(), provider);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<AiExecutionResponse> getExecutions(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException("Invalid pagination parameters");
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AiExecutionResponse> result = executionRepository.findForList(TenantContext.scopeOrAllForSuperAdmin(), pageable)
                .map(aiMapper::toResponse);
        return PagedResponse.from(result);
    }

    // ---------- deterministic path ----------

    private MatchResponse deterministicResponse(MatchComparator.Comparison cmp) {
        MatchDecision decision = deriveDecision(cmp.getScore(), cmp.isHardConflict());
        double confidence = Math.round(cmp.getCoverage() * 100.0) / 100.0;
        String reason = String.format(
                "Deterministic comparison: score %d/100, matched %s, %d conflict(s)%s. Coverage %.0f%%.",
                cmp.getScore(), cmp.getMatchedAttributes(), cmp.getConflicts().size(),
                cmp.isHardConflict() ? " (includes HARD conflict)" : "", cmp.getCoverage() * 100);
        return MatchResponse.builder()
                .score(cmp.getScore())
                .decision(decision)
                .reason(reason)
                .conflicts(cmp.getConflicts())
                .missingEvidence(cmp.getMissingEvidence())
                .matchedAttributes(cmp.getMatchedAttributes())
                .confidence(confidence)
                .build();
    }

    private MatchDecision deriveDecision(int score, boolean hardConflict) {
        if (hardConflict) {
            return score >= 50 ? MatchDecision.REVIEW : MatchDecision.NO_MATCH;
        }
        if (score >= 85) {
            return MatchDecision.MATCH;
        }
        if (score >= 60) {
            return MatchDecision.POSSIBLE_MATCH;
        }
        if (score >= 40) {
            return MatchDecision.REVIEW;
        }
        return MatchDecision.NO_MATCH;
    }

    // ---------- AI path ----------

    private AiCompletionRequest buildAiRequest(ComparableProduct a, ComparableProduct b,
                                               MatchComparator.Comparison cmp) {
        String system = """
                You are a product-matching expert for an e-commerce price intelligence platform.
                Compare the PRODUCT and CANDIDATE across: brand, title, model, color, variant,
                pack quantity, and image. Return ONLY a JSON object with these keys:
                  score (integer 0-100),
                  decision (one of MATCH, POSSIBLE_MATCH, NO_MATCH, REVIEW),
                  reason (string),
                  conflicts (array of objects: field, productValue, candidateValue, severity[HARD|SOFT]),
                  missingEvidence (array of strings),
                  confidence (number between 0 and 1).
                Rules: Never invent attribute values. If a value is absent, list the field in missingEvidence.
                A different pack quantity or model is a HARD conflict. Do not output anything except the JSON.
                """;
        String user;
        try {
            user = "PRODUCT:\n" + objectMapper.writeValueAsString(a)
                    + "\n\nCANDIDATE:\n" + objectMapper.writeValueAsString(b)
                    + "\n\nDeterministic hints (authoritative on conflicts): "
                    + objectMapper.writeValueAsString(cmp.getConflicts());
        } catch (Exception e) {
            user = "PRODUCT: " + a + "\nCANDIDATE: " + b;
        }
        return AiCompletionRequest.builder()
                .systemPrompt(system).userPrompt(user).temperature(0.0).jsonOnly(true).maxTokens(600)
                .build();
    }

    private MatchResponse parseAiResponse(String content, MatchComparator.Comparison fallback) {
        try {
            JsonNode root = objectMapper.readTree(content);
            int score = root.path("score").asInt(fallback.getScore());
            MatchDecision decision = MatchDecision.valueOf(
                    root.path("decision").asText(deriveDecision(score, fallback.isHardConflict()).name()));
            double confidence = root.path("confidence").asDouble(fallback.getCoverage());

            List<MatchConflict> conflicts = new ArrayList<>();
            for (JsonNode cf : root.path("conflicts")) {
                conflicts.add(MatchConflict.builder()
                        .field(cf.path("field").asText(null))
                        .productValue(cf.path("productValue").asText(null))
                        .candidateValue(cf.path("candidateValue").asText(null))
                        .severity(cf.path("severity").asText(null))
                        .build());
            }
            if (conflicts.isEmpty()) {
                conflicts = fallback.getConflicts(); // rules authoritative when AI omits
            }

            List<String> missing = new ArrayList<>();
            for (JsonNode m : root.path("missingEvidence")) {
                missing.add(m.asText());
            }
            if (missing.isEmpty()) {
                missing = fallback.getMissingEvidence();
            }

            return MatchResponse.builder()
                    .score(score)
                    .decision(decision)
                    .reason(root.path("reason").asText(""))
                    .conflicts(conflicts)
                    .missingEvidence(missing)
                    .matchedAttributes(fallback.getMatchedAttributes())
                    .confidence(confidence)
                    .build();
        } catch (Exception e) {
            throw new com.priceintel.backend.exception.AiException(
                    "AI returned invalid JSON: " + e.getMessage(), e);
        }
    }

    // ---------- building ComparableProduct ----------

    private ComparableProduct fromProduct(Product p) {
        ProductAttribute attr = (p.getAttributes() != null && !p.getAttributes().isEmpty())
                ? p.getAttributes().get(0) : null;
        String imageUrl = (p.getImages() != null && !p.getImages().isEmpty())
                ? p.getImages().get(0).getUrl() : null;
        return ComparableProduct.builder()
                .brand(p.getBrand())
                .title(p.getTitle())
                .model(attr != null ? attr.getModel() : null)
                .color(attr != null ? attr.getColor() : null)
                .variant(attr != null ? attr.getVariant() : null)
                .packQuantity(p.getPackQuantity())
                .imageUrl(imageUrl)
                .build();
    }

    private ComparableProduct resolveCandidate(MatchRequest request) {
        if (request.getCandidateId() != null) {
            Product candidate = productRepository.findById(request.getCandidateId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Candidate product not found: " + request.getCandidateId()));
            return fromProduct(candidate);
        }
        MatchCandidate c = request.getCandidate();
        return ComparableProduct.builder()
                .brand(c.getBrand()).title(c.getTitle()).model(c.getModel())
                .color(c.getColor()).variant(c.getVariant())
                .packQuantity(c.getPackQuantity()).imageUrl(c.getImageUrl())
                .build();
    }
}
