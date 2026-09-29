package com.priceintel.backend.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceintel.backend.dto.request.ReportRequest;
import com.priceintel.backend.dto.response.ReportResponse;
import com.priceintel.backend.entity.CompetitorListing;
import com.priceintel.backend.entity.Product;
import com.priceintel.backend.repository.CompetitorListingRepository;
import com.priceintel.backend.repository.ProductRepository;
import com.priceintel.backend.security.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Generates competitor-price reports (Reports page). Joins competitor listings
 * with their products, applies marketplace/category/date filters, and renders
 * CSV or JSON. Returns preview rows plus the full downloadable content.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final CompetitorListingRepository listingRepo;
    private final ProductRepository productRepo;
    private final ObjectMapper objectMapper;
    private final com.priceintel.backend.utils.ValueStatusResolver statusResolver;

    /**
     * "Price Status" sits directly after "Price" on purpose: FR-REPORT-001
     * requires every exported number to carry its provenance, and a status
     * column separated from its value is one sort away from being misread.
     * "Price Observed" gives the reader the actual age rather than a label.
     */
    private static final List<String> COLUMNS = List.of(
            "SKU", "Product", "Category", "Marketplace", "Seller",
            "Price", "Price Status", "Currency", "Condition", "Match Status",
            "Last Updated", "Price Observed");
    private static final int PREVIEW_LIMIT = 100;

    @Transactional(readOnly = true)
    public ReportResponse generate(ReportRequest req) {
        String format = req.getFormat() != null && req.getFormat().equalsIgnoreCase("json") ? "JSON" : "CSV";
        String marketplace = normalize(req.getMarketplace());
        String category = normalize(req.getCategory());
        Instant from = req.getFrom() != null ? req.getFrom().atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        Instant to = req.getTo() != null ? req.getTo().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : null;

        // Scoped to the caller's tenant: an export is the easiest way for one
        // client's competitor research to leave the platform in another's hands.
        Long tenantId = TenantContext.scopeOrAllForSuperAdmin();
        List<CompetitorListing> listings = tenantId == null
                ? listingRepo.findAll()
                : listingRepo.findByTenantId(tenantId);
        // Resolve products once.
        Map<Long, Product> products = new java.util.HashMap<>();
        productRepo.findAllById(listings.stream().map(CompetitorListing::getProductId)
                .collect(java.util.stream.Collectors.toSet())).forEach(p -> products.put(p.getId(), p));

        List<List<String>> rows = new ArrayList<>();
        for (CompetitorListing l : listings) {
            if (marketplace != null && (l.getMarketplace() == null
                    || !l.getMarketplace().equalsIgnoreCase(marketplace))) {
                continue;
            }
            if (from != null && (l.getSourceTimestamp() == null || l.getSourceTimestamp().isBefore(from))) {
                continue;
            }
            if (to != null && (l.getSourceTimestamp() == null || !l.getSourceTimestamp().isBefore(to))) {
                continue;
            }
            Product p = products.get(l.getProductId());
            String cat = p != null && p.getCategory() != null ? p.getCategory().getName() : null;
            if (category != null && (cat == null || !cat.equalsIgnoreCase(category))) {
                continue;
            }
            rows.add(List.of(
                    nn(p != null ? p.getSku() : null),
                    nn(p != null ? p.getTitle() : null),
                    nn(cat),
                    nn(l.getMarketplace()),
                    nn(l.getSeller()),
                    l.getLastPrice() != null ? l.getLastPrice().toPlainString() : "",
                    // FR-REPORT-001: every exported number carries its status.
                    // A spreadsheet is exactly where provenance is otherwise
                    // lost — the reader cannot tell a live price from a
                    // week-old one once it is a cell in a column.
                    statusResolver.forObservation(l.getLastPrice(), l.getLastFetchedAt()).name(),
                    nn(l.getCurrency()),
                    nn(l.getCondition()),
                    l.getMatchStatus() != null ? l.getMatchStatus().name() : "",
                    l.getSourceTimestamp() != null ? l.getSourceTimestamp().toString() : "",
                    l.getLastFetchedAt() != null ? l.getLastFetchedAt().toString() : ""));
        }

        String date = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        String content;
        String filename;
        String mime;
        if ("JSON".equals(format)) {
            content = toJson(rows);
            filename = "competitor-prices-" + date + ".json";
            mime = "application/json";
        } else {
            content = toCsv(rows);
            filename = "competitor-prices-" + date + ".csv";
            mime = "text/csv";
        }

        List<List<String>> preview = rows.size() > PREVIEW_LIMIT ? rows.subList(0, PREVIEW_LIMIT) : rows;
        log.info("Generated {} report: {} rows (marketplace={}, category={})",
                format, rows.size(), marketplace, category);
        return ReportResponse.builder()
                .reportType("COMPETITOR_PRICES").format(format)
                .filename(filename).mimeType(mime)
                .columns(COLUMNS).rows(new ArrayList<>(preview)).rowCount(rows.size())
                .content(content).generatedAt(Instant.now())
                .build();
    }

    // ---------- helpers ----------

    private String normalize(String v) {
        return v == null || v.isBlank() || v.equalsIgnoreCase("all") ? null : v.trim();
    }

    private String nn(String v) {
        return v == null ? "" : v;
    }

    private String toCsv(List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", COLUMNS)).append("\n");
        for (List<String> row : rows) {
            List<String> escaped = new ArrayList<>();
            for (String cell : row) {
                escaped.add(csvEscape(cell));
            }
            sb.append(String.join(",", escaped)).append("\n");
        }
        return sb.toString();
    }

    private String csvEscape(String s) {
        if (s == null) {
            return "";
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private String toJson(List<List<String>> rows) {
        List<Map<String, String>> objects = new ArrayList<>();
        for (List<String> row : rows) {
            Map<String, String> o = new LinkedHashMap<>();
            for (int i = 0; i < COLUMNS.size(); i++) {
                o.put(COLUMNS.get(i), i < row.size() ? row.get(i) : "");
            }
            objects.add(o);
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objects);
        } catch (Exception e) {
            return "[]";
        }
    }
}
