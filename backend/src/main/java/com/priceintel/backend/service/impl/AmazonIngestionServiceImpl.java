package com.priceintel.backend.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.priceintel.backend.dto.request.CreateProductRequest;
import com.priceintel.backend.dto.response.ProductResponse;
import com.priceintel.backend.entity.RawSourceRecord;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.mapper.ProductMapper;
import com.priceintel.backend.marketplace.amazon.AmazonProductNormalizer;
import com.priceintel.backend.marketplace.amazon.AmazonSpApiClient;
import com.priceintel.backend.repository.ProductRepository;
import com.priceintel.backend.repository.RawSourceRecordRepository;
import com.priceintel.backend.service.AmazonIngestionService;
import com.priceintel.backend.service.ProductService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AmazonIngestionServiceImpl implements AmazonIngestionService {

    private final AmazonSpApiClient client;
    private final AmazonProductNormalizer normalizer;
    private final RawSourceRecordRepository rawRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final ProductMapper productMapper;

    private static final String SOURCE = "AMAZON";

    @Override
    @Transactional
    public ProductResponse ingestByAsin(String asin) {
        if (asin == null || asin.isBlank()) {
            throw new BadRequestException("ASIN is required");
        }
        String cleanAsin = asin.trim().toUpperCase();

        // 1. Fetch from Amazon (these throw MarketplaceApiException if not configured).
        String catalogJson = client.getCatalogItem(cleanAsin);
        storeRaw(cleanAsin, "CATALOG", catalogJson);

        String pricingJson = client.getPricing(cleanAsin);
        storeRaw(cleanAsin, "PRICING", pricingJson);

        // 2. Normalize to a canonical product.
        CreateProductRequest request = normalizer.toCreateProductRequest(catalogJson, cleanAsin);

        // 3. Store canonical product (upsert by SKU = ASIN).
        ProductResponse response = productRepository.findByTenantIdAndSkuIgnoreCase(
                        com.priceintel.backend.security.TenantContext.getTenantId(), request.getSku())
                .stream().findFirst()   // this company's product only, never another client's
                .map(existing -> {
                    log.info("Canonical product already exists for ASIN {} (id={})", cleanAsin, existing.getId());
                    return productMapper.toResponse(existing);
                })
                .orElseGet(() -> productService.createProduct(request));

        log.info("Ingested Amazon ASIN {} -> product id {}", cleanAsin, response.getId());
        return response;
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
