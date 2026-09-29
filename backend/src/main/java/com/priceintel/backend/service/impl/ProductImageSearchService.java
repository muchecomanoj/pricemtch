package com.priceintel.backend.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.priceintel.backend.dto.response.ImageSearchMatch;
import com.priceintel.backend.entity.Product;
import com.priceintel.backend.entity.ProductImage;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.repository.ProductImageRepository;
import com.priceintel.backend.security.TenantContext;
import com.priceintel.backend.utils.ImageHashService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Image-based product search (Search Product → Search by image). Compares the
 * uploaded image's perceptual hash to stored product-image hashes and returns
 * the closest matches — scoped to the caller's tenant.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductImageSearchService {

    private final ProductImageRepository imageRepo;
    private final ImageHashService hashService;

    /** Max Hamming distance (of 64) to count as a match (~81% similar). */
    private static final int MATCH_THRESHOLD = 12;
    private static final int MAX_RESULTS = 20;

    @Transactional(readOnly = true)
    public List<ImageSearchMatch> searchByImage(String imageSrc) {
        String queryHash = hashService.hash(imageSrc)
                .orElseThrow(() -> new BadRequestException(
                        "Could not read that image. Upload a valid PNG/JPG."));

        Long tenantId = TenantContext.isSuperAdmin() ? null : TenantContext.getTenantId();
        List<ProductImage> candidates = imageRepo.findHashedForTenant(tenantId);

        // Best (smallest) distance per product.
        Map<Long, ImageSearchMatch> bestByProduct = new HashMap<>();
        for (ProductImage img : candidates) {
            int d = hashService.distance(queryHash, img.getHash());
            if (d > MATCH_THRESHOLD) {
                continue;
            }
            Product p = img.getProduct();
            ImageSearchMatch existing = bestByProduct.get(p.getId());
            if (existing == null || d < existing.getDistance()) {
                bestByProduct.put(p.getId(), ImageSearchMatch.builder()
                        .productId(p.getId()).sku(p.getSku()).title(p.getTitle())
                        .brand(p.getBrand())
                        .category(p.getCategory() != null ? p.getCategory().getName() : null)
                        .imageUrl(img.getUrl())
                        .distance(d).similarityPct(hashService.similarityPct(d))
                        .build());
            }
        }
        List<ImageSearchMatch> results = new ArrayList<>(bestByProduct.values());
        results.sort(Comparator.comparingInt(ImageSearchMatch::getDistance));
        return results.size() > MAX_RESULTS ? results.subList(0, MAX_RESULTS) : results;
    }

    /** One-time backfill: hash any product image that has no hash yet. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfillHashes() {
        List<ProductImage> unhashed = imageRepo.findByHashIsNull();
        int done = 0;
        for (ProductImage img : unhashed) {
            var h = hashService.hash(img.getUrl());
            if (h.isPresent()) {
                img.setHash(h.get());
                imageRepo.save(img);
                done++;
            }
        }
        if (done > 0) {
            log.info("Backfilled perceptual hashes for {} product images", done);
        }
    }
}
