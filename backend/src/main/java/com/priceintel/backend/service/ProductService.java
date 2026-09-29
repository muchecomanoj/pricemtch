package com.priceintel.backend.service;

import com.priceintel.backend.constants.ProductStatus;
import com.priceintel.backend.dto.request.CreateProductRequest;
import com.priceintel.backend.dto.request.UpdateProductRequest;
import com.priceintel.backend.dto.response.PagedResponse;
import com.priceintel.backend.dto.response.ProductFilterOptionsResponse;
import com.priceintel.backend.dto.response.ProductResponse;

/**
 * Product master use cases (Phase 3).
 */
public interface ProductService {

    ProductResponse createProduct(CreateProductRequest request);

    ProductResponse getProductById(Long id);

    ProductResponse updateProduct(Long id, UpdateProductRequest request);

    void deleteProduct(Long id);

    /** Brands, categories and statuses available to filter this catalogue by. */
    ProductFilterOptionsResponse getFilterOptions();

    PagedResponse<ProductResponse> listProducts(String keyword, ProductStatus status, String brand,
                                                String category, String identifier,
                                                int page, int size, String sortBy, String direction);

    /**
     * @param hasCompetitors {@code true} for products with at least one
     *                       non-rejected competitor listing, {@code false} for
     *                       those with none, {@code null} for no filter
     */
    PagedResponse<ProductResponse> listProducts(String keyword, ProductStatus status, String brand,
                                                String category, String identifier, Boolean hasCompetitors,
                                                int page, int size, String sortBy, String direction);
}
