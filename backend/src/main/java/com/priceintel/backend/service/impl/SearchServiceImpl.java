package com.priceintel.backend.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceintel.backend.constants.IdentifierType;
import com.priceintel.backend.constants.SearchStage;
import com.priceintel.backend.constants.SearchStepStatus;
import com.priceintel.backend.dto.request.SearchRequest;
import com.priceintel.backend.dto.response.PagedResponse;
import com.priceintel.backend.dto.response.ProductResponse;
import com.priceintel.backend.dto.response.SearchHistoryResponse;
import com.priceintel.backend.dto.response.SearchResponse;
import com.priceintel.backend.dto.response.SearchTraceStep;
import com.priceintel.backend.entity.Product;
import com.priceintel.backend.entity.SearchHistory;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.mapper.ProductMapper;
import com.priceintel.backend.mapper.SearchMapper;
import com.priceintel.backend.repository.ProductIdentifierRepository;
import com.priceintel.backend.repository.ProductImageRepository;
import com.priceintel.backend.repository.ProductRepository;
import com.priceintel.backend.repository.SearchHistoryRepository;
import com.priceintel.backend.security.TenantContext;
import com.priceintel.backend.service.SearchService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs the search waterfall against the local database. Priority order:
 * ASIN, GTIN, EAN, UPC, MPN, SKU, TITLE, URL, IMAGE(placeholder). The first
 * stage that returns results wins; every stage is recorded in the trace.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final ProductRepository productRepository;
    private final ProductIdentifierRepository identifierRepository;
    private final ProductImageRepository imageRepository;
    private final SearchHistoryRepository searchHistoryRepository;
    private final ProductMapper productMapper;
    private final SearchMapper searchMapper;
    private final ObjectMapper objectMapper;

    /** Cap on results for the broad (title/url) stages. */
    private static final int MAX_RESULTS = 50;

    private static final List<SearchStage> WATERFALL = List.of(
            SearchStage.ASIN, SearchStage.GTIN, SearchStage.EAN, SearchStage.UPC, SearchStage.MPN,
            SearchStage.SKU, SearchStage.TITLE, SearchStage.URL, SearchStage.IMAGE);

    @Override
    @Transactional
    public SearchResponse search(SearchRequest request) {
        String query = request.getQuery() == null ? "" : request.getQuery().trim();
        if (query.isEmpty()) {
            throw new BadRequestException("Search query must not be empty");
        }
        String normalizedId = query.toUpperCase().replaceAll("[\\s-]", "");

        List<SearchTraceStep> trace = new ArrayList<>();
        List<Product> matches = new ArrayList<>();
        SearchStage matchedStage = SearchStage.NONE;
        boolean matched = false;

        for (SearchStage stage : WATERFALL) {
            if (matched) {
                trace.add(step(stage, valueForStage(stage, query, normalizedId),
                        SearchStepStatus.SKIPPED, 0, "Skipped — an earlier stage already matched"));
                continue;
            }

            if (stage == SearchStage.IMAGE) {
                // Placeholder: visual matching (perceptual hash / embeddings) is a future phase.
                trace.add(step(stage, "[image]", SearchStepStatus.NOT_IMPLEMENTED, 0,
                        "Image-based search is a placeholder; requires visual matching (future phase)"));
                continue;
            }

            List<Product> found = runStage(stage, query, normalizedId);
            if (!found.isEmpty()) {
                matched = true;
                matchedStage = stage;
                matches = found;
                trace.add(step(stage, valueForStage(stage, query, normalizedId),
                        SearchStepStatus.MATCHED, found.size(), "Matched"));
            } else {
                trace.add(step(stage, valueForStage(stage, query, normalizedId),
                        SearchStepStatus.NO_MATCH, 0, "No match"));
            }
        }

        List<ProductResponse> results = matches.stream().map(productMapper::toResponse).toList();
        recordHistory(query, matchedStage, results.size(), trace);

        log.info("Search '{}' matched stage {} with {} result(s)", query, matchedStage, results.size());
        return SearchResponse.builder()
                .query(query)
                .matchedStage(matchedStage)
                .totalMatches(results.size())
                .trace(trace)
                .results(results)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<SearchHistoryResponse> getHistory(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BadRequestException("Invalid pagination parameters");
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<SearchHistoryResponse> result = searchHistoryRepository.findForList(TenantContext.scopeOrAllForSuperAdmin(), pageable)
                .map(searchMapper::toResponse);
        return PagedResponse.from(result);
    }

    // ---------- waterfall stages ----------

    /**
     * Searches the caller's own catalogue only.
     *
     * <p>Every stage used to search all companies at once. A search for
     * "Apple AirPods 4" in Patuli's account listed Price Matrix's product under
     * "In your catalogue" — and opening it showed an empty page, because the
     * product page itself is correctly limited to the caller's company.</p>
     *
     * <p>The platform owner, who has no company, still searches everything.
     * Anyone else without a company sees nothing rather than everything.</p>
     */
    private List<Product> runStage(SearchStage stage, String query, String normalizedId) {
        Pageable limit = PageRequest.of(0, MAX_RESULTS);
        boolean everyone = TenantContext.isSuperAdmin();
        Long tenantId = TenantContext.getTenantId();
        if (!everyone && tenantId == null) {
            return List.of();
        }
        return switch (stage) {
            case ASIN, GTIN, EAN, UPC, MPN -> byIdentifier(IdentifierType.valueOf(stage.name()), normalizedId,
                    everyone ? null : tenantId);
            case SKU -> everyone
                    ? productRepository.findBySkuIgnoreCase(query).map(List::of).orElseGet(List::of)
                    : productRepository.findByTenantIdAndSkuIgnoreCase(tenantId, query);
            case TITLE -> everyone
                    ? productRepository.findByTitleContainingIgnoreCase(query, limit)
                    : productRepository.findByTenantIdAndTitleContainingIgnoreCase(tenantId, query, limit);
            case URL -> everyone
                    ? imageRepository.findProductsByImageUrlContaining(query, limit)
                    : imageRepository.findProductsByTenantAndImageUrlContaining(tenantId, query, limit);
            default -> List.of();
        };
    }

    /** {@code tenantId} null means every company — the platform owner only. */
    private List<Product> byIdentifier(IdentifierType type, String normalizedValue, Long tenantId) {
        // A product may hold several identifiers; de-duplicate by product id.
        Map<Long, Product> distinct = new LinkedHashMap<>();
        (tenantId == null
                ? identifierRepository.findByTypeAndNormalizedValue(type, normalizedValue)
                : identifierRepository.findByTenantIdAndTypeAndNormalizedValue(tenantId, type, normalizedValue))
                .forEach(pi -> distinct.putIfAbsent(pi.getProduct().getId(), pi.getProduct()));
        return new ArrayList<>(distinct.values());
    }

    private String valueForStage(SearchStage stage, String query, String normalizedId) {
        return switch (stage) {
            case ASIN, GTIN, EAN, UPC, MPN -> normalizedId;
            default -> query;
        };
    }

    private SearchTraceStep step(SearchStage stage, String value, SearchStepStatus status, int count, String note) {
        return SearchTraceStep.builder()
                .stage(stage).queryValue(value).status(status).matchCount(count).note(note).build();
    }

    private void recordHistory(String query, SearchStage matchedStage, int matchCount, List<SearchTraceStep> trace) {
        searchHistoryRepository.save(SearchHistory.builder()
                .tenantId(TenantContext.getTenantId())
                .query(query)
                .matchedStage(matchedStage)
                .matchCount(matchCount)
                .traceJson(toJson(trace))
                .build());
    }

    private String toJson(List<SearchTraceStep> trace) {
        try {
            String json = objectMapper.writeValueAsString(trace);
            return json.length() <= 4000 ? json : json.substring(0, 4000);
        } catch (Exception e) {
            return null;
        }
    }
}
