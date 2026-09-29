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
import com.priceintel.backend.marketplace.ebay.EbayApiClient;
import com.priceintel.backend.marketplace.ebay.EbayResponseNormalizer;
import com.priceintel.backend.repository.ProductRepository;
import com.priceintel.backend.repository.RawSourceRecordRepository;
import com.priceintel.backend.service.EbayIngestionService;
import com.priceintel.backend.service.ProductService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EbayIngestionServiceImpl implements EbayIngestionService {

    private final EbayApiClient client;
    private final EbayResponseNormalizer normalizer;
    private final RawSourceRecordRepository rawRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final ProductMapper productMapper;

    private static final String SOURCE = "EBAY";

    @Override
    @Transactional
    public ProductResponse ingestByItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            throw new BadRequestException("eBay itemId is required");
        }
        String clean = itemId.trim();

        // 1. Fetch the full listing (throws MarketplaceApiException if not configured).
        String itemJson = client.getItem(clean);
        storeRaw(clean, "LISTING", itemJson);

        // 2. Normalize to a canonical product.
        CreateProductRequest request = normalizer.toCreateProductRequest(itemJson, clean);

        // 3. Store canonical product (upsert by SKU = EBAY-<itemId>).
        ProductResponse response = productRepository.findByTenantIdAndSkuIgnoreCase(
                        com.priceintel.backend.security.TenantContext.getTenantId(), request.getSku())
                .stream().findFirst()   // this company's product only, never another client's
                .map(existing -> {
                    log.info("Canonical product already exists for eBay item {} (id={})", clean, existing.getId());
                    return productMapper.toResponse(existing);
                })
                .orElseGet(() -> productService.createProduct(request));

        log.info("Ingested eBay item {} -> product id {}", clean, response.getId());
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
