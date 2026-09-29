package com.priceintel.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.priceintel.backend.constants.ProductStatus;
import com.priceintel.backend.dto.request.CreateProductRequest;
import com.priceintel.backend.dto.request.UpdateProductRequest;
import com.priceintel.backend.dto.response.ApiResponse;
import com.priceintel.backend.dto.response.PagedResponse;
import com.priceintel.backend.dto.response.ProductFilterOptionsResponse;
import com.priceintel.backend.dto.response.ProductResponse;
import com.priceintel.backend.service.ProductService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Product master endpoints (Phase 3).
 *   - Reads (view/list/search) are open to any authenticated user.
 *   - Writes (create/update/delete) require a management role (RBAC).
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Product Master", description = "Create, read, update, delete, and search products")
public class ProductController {

    private static final String WRITE_ROLES = "hasAnyRole('ADMIN', 'MANAGER')";

    private final ProductService productService;
    private final com.priceintel.backend.service.impl.ProductImageSearchService imageSearchService;
    private final com.priceintel.backend.service.impl.ProductExportService exportService;
    private final com.priceintel.backend.service.impl.AsinSuggestionService asinSuggestions;

    @PostMapping("/{id}/suggest-asin")
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Find the real ASIN for this product",
            description = "Searches the web for the product by title, brand and MPN, then verifies "
                    + "every candidate against Amazon and returns only those that resolve — with "
                    + "Amazon's own title and price, not the model's. Nothing is saved: the "
                    + "suggestions are for a person to accept. Returns 400 when no web-search "
                    + "model is configured.")
    public ResponseEntity<ApiResponse<com.priceintel.backend.dto.response.AsinLookupResponse>>
            suggestAsin(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                asinSuggestions.suggestFor(id), "ASIN suggestions"));
    }

    @GetMapping("/export")
    @Operation(summary = "Download the filtered product catalog as CSV",
            description = "Takes the same filters as the list endpoint, so the file matches what is on "
                    + "screen. Column headers are the ones the importer accepts, so an export can be "
                    + "edited and uploaded back.")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String identifier) {
        byte[] csv = exportService.exportCsv(search, status, brand, category, identifier);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=products.csv")
                .contentType(org.springframework.http.MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    @PostMapping("/search-by-image")
    @Operation(summary = "Find products in your catalog whose image matches an uploaded image (perceptual hash)")
    public ResponseEntity<ApiResponse<java.util.List<com.priceintel.backend.dto.response.ImageSearchMatch>>> searchByImage(
            @Valid @RequestBody com.priceintel.backend.dto.request.ImageSearchRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                imageSearchService.searchByImage(request.getImage()), "Image search results"));
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Create a product with identifiers, images, and attributes")
    public ResponseEntity<ApiResponse<ProductResponse>> create(@Valid @RequestBody CreateProductRequest request) {
        ProductResponse created = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Product created successfully"));
    }

    @GetMapping("/filter-options")
    @Operation(summary = "Brands, categories and statuses available for filtering this catalogue",
            description = "Populates the Products page dropdowns. Brands and categories are the values "
                    + "actually present in the caller's own catalogue, so a filter never offers a choice "
                    + "that would return nothing.")
    public ResponseEntity<ApiResponse<ProductFilterOptionsResponse>> filterOptions() {
        return ResponseEntity.ok(ApiResponse.success(
                productService.getFilterOptions(), "Filter options retrieved"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "View a single product by id")
    public ResponseEntity<ApiResponse<ProductResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(productService.getProductById(id), "Product retrieved"));
    }

    @GetMapping
    @Operation(summary = "List / search / filter products with pagination and sorting",
            description = "search matches SKU, title, brand, description and identifiers (ASIN/UPC/EAN/"
                    + "GTIN/MPN), so an ASIN pasted from Amazon finds the product. identifier is a "
                    + "separate filter for matching on identifiers only.\n\n"
                    + "Every product carries `competitorCount` — non-rejected competitor listings, "
                    + "the same set the alert engine and price statistics read. Zero means no "
                    + "price comparison is possible yet.\n\n"
                    + "`hasCompetitors=true` narrows the list to those products, and `false` to "
                    + "those without. It filters in the database, so \"select every product with "
                    + "competitors\" covers the whole catalogue rather than the page on screen.")
    public ResponseEntity<ApiResponse<PagedResponse<ProductResponse>>> list(
            @RequestParam(required = false) String search,
            // productService.list on the frontend names it "q"; accepted so a
            // search passed that way is not silently dropped.
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String identifier,
            @RequestParam(required = false) Boolean hasCompetitors,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        PagedResponse<ProductResponse> result = productService.listProducts(
                search != null && !search.isBlank() ? search : q,
                status, brand, category, identifier, hasCompetitors,
                page, size, sortBy, direction);
        return ResponseEntity.ok(ApiResponse.success(result, "Products retrieved"));
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Update a product (partial; collections replaced when supplied)")
    public ResponseEntity<ApiResponse<ProductResponse>> update(
            @PathVariable Long id, @Valid @RequestBody UpdateProductRequest request) {
        return ResponseEntity.ok(ApiResponse.success(productService.updateProduct(id, request), "Product updated"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(WRITE_ROLES)
    @Operation(summary = "Delete a product")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success("Product deleted successfully"));
    }
}
