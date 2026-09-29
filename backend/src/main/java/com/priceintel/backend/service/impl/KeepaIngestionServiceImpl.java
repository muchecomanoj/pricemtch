package com.priceintel.backend.service.impl;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.priceintel.backend.dto.request.CreateProductRequest;
import com.priceintel.backend.dto.response.ProductResponse;
import com.priceintel.backend.entity.RawSourceRecord;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.mapper.ProductMapper;
import com.priceintel.backend.marketplace.keepa.KeepaClient;
import com.priceintel.backend.marketplace.keepa.KeepaNormalizer;
import com.priceintel.backend.marketplace.keepa.KeepaProperties;
import com.priceintel.backend.repository.ProductRepository;
import com.priceintel.backend.repository.RawSourceRecordRepository;
import com.priceintel.backend.service.KeepaIngestionService;
import com.priceintel.backend.service.ProductService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeepaIngestionServiceImpl implements KeepaIngestionService {

    private final KeepaClient client;
    private final KeepaNormalizer normalizer;
    private final KeepaProperties props;
    private final RawSourceRecordRepository rawRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final ProductMapper productMapper;

    // Kept as AMAZON so Keepa-sourced data joins the existing Amazon records.
    private static final String SOURCE = "AMAZON";

    @Override
    @Transactional
    public ProductResponse ingestByAsin(String asin) {
        String cleanAsin = requireAsin(asin);

        String productJson = client.getProduct(cleanAsin);
        storeRaw(cleanAsin, "KEEPA_PRODUCT", productJson);

        CreateProductRequest request = normalizer.toCreateProductRequest(productJson, cleanAsin);

        ProductResponse response = productRepository.findByTenantIdAndSkuIgnoreCase(
                        com.priceintel.backend.security.TenantContext.getTenantId(), request.getSku())
                .stream().findFirst()   // this company's product only, never another client's
                .map(existing -> {
                    log.info("Canonical product already exists for ASIN {} (id={})", cleanAsin, existing.getId());
                    return productMapper.toResponse(existing);
                })
                .orElseGet(() -> productService.createProduct(request));

        log.info("Ingested Keepa ASIN {} -> product id {}", cleanAsin, response.getId());
        return response;
    }

    @Override
    @Transactional
    public Map<String, Object> priceByAsin(String asin) {
        String cleanAsin = requireAsin(asin);
        String productJson = client.getProduct(cleanAsin);
        storeRaw(cleanAsin, "KEEPA_PRICE", productJson);

        BigDecimal current = normalizer.extractCurrentPrice(productJson);
        List<KeepaNormalizer.PricePoint> history = normalizer.priceHistory(productJson);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("asin", cleanAsin);
        result.put("currentPrice", current);
        result.put("currency", props.currency());
        result.put("historyPoints", history.size());
        // Return the most recent 30 points to keep the payload light.
        int from = Math.max(0, history.size() - 30);
        result.put("recentHistory", history.subList(from, history.size()));
        return result;
    }

    private String requireAsin(String asin) {
        if (asin == null || asin.isBlank()) {
            throw new BadRequestException("ASIN is required");
        }
        return asin.trim().toUpperCase();
    }

    private void storeRaw(String externalId, String operation, String payload) {
        rawRepository.save(RawSourceRecord.builder()
                .source(SOURCE)
                .externalId(externalId)
                .operation(operation)
                .checksum(sha256(payload))
                .payload(payload)
                .build());
    }

    private String sha256(String value) {
        if (value == null) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
