package com.priceintel.backend.service.impl;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.priceintel.backend.constants.IdentifierType;
import com.priceintel.backend.constants.ImageStatus;
import com.priceintel.backend.constants.ProductCondition;
import com.priceintel.backend.constants.ProductStatus;
import com.priceintel.backend.dto.request.CreateProductRequest;
import com.priceintel.backend.dto.request.ProductAttributeRequest;
import com.priceintel.backend.dto.request.ProductIdentifierRequest;
import com.priceintel.backend.dto.request.ProductImageRequest;
import com.priceintel.backend.dto.request.UpdateProductRequest;
import com.priceintel.backend.dto.response.PagedResponse;
import com.priceintel.backend.dto.response.ProductFilterOptionsResponse;
import com.priceintel.backend.dto.response.ProductResponse;
import com.priceintel.backend.entity.Product;
import com.priceintel.backend.entity.ProductAttribute;
import com.priceintel.backend.entity.ProductCategory;
import com.priceintel.backend.entity.ProductIdentifier;
import com.priceintel.backend.entity.ProductImage;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.exception.DuplicateResourceException;
import com.priceintel.backend.exception.ResourceNotFoundException;
import com.priceintel.backend.mapper.ProductMapper;
import com.priceintel.backend.repository.ProductCategoryRepository;
import com.priceintel.backend.repository.ProductRepository;
import com.priceintel.backend.repository.ProductSpecifications;
import com.priceintel.backend.security.TenantContext;
import com.priceintel.backend.service.ProductService;
import com.priceintel.backend.utils.IdentifierNormalizer;

import org.springframework.data.jpa.domain.Specification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final com.priceintel.backend.repository.ProductIdentifierRepository identifierRepository;
    private final ProductMapper productMapper;
    private final com.priceintel.backend.utils.ImageHashService imageHashService;
    private final com.priceintel.backend.repository.CompetitorListingRepository listingRepository;

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("id", "sku", "title", "brand", "status", "packQuantity", "createdAt", "updatedAt");

    @Override
    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        if (productRepository.existsByTenantIdAndSkuIgnoreCase(TenantContext.getTenantId(), request.getSku())) {
            throw new DuplicateResourceException("You already have a product with SKU " + request.getSku());
        }
        requireSearchableIdentifier(request.getIdentifiers());

        Product product = Product.builder()
                .sku(request.getSku())
                .title(request.getTitle())
                .brand(request.getBrand())
                .description(request.getDescription())
                .category(resolveCategory(request.getCategory()))
                .status(request.getStatus() != null ? request.getStatus() : ProductStatus.ACTIVE)
                .packQuantity(request.getPackQuantity() != null ? request.getPackQuantity() : 1)
                .condition(request.getCondition() != null ? request.getCondition() : ProductCondition.NEW)
                .tenantId(TenantContext.getTenantId())   // stamp the owning tenant (isolation)
                .ourPrice(request.getOurPrice())
                .weight(request.getWeight())
                .height(request.getHeight())
                .width(request.getWidth())
                .length(request.getLength())
                .build();

        applyIdentifiers(product, request.getIdentifiers());
        applyImages(product, request.getImages());
        applyAttributes(product, request.getAttributes());

        product = productRepository.save(product);
        log.info("Created product id={} sku={}", product.getId(), product.getSku());
        return productMapper.toResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        ProductResponse response = productMapper.toResponse(findProductOrThrow(id));
        response.setCompetitorCount(listingRepository.countByProductIdAndMatchStatusNot(
                id, com.priceintel.backend.constants.MatchStatus.REJECTED));
        response.setPricedCompetitorCount(
                listingRepository.countByProductIdAndMatchStatusNotAndLastPriceIsNotNull(
                        id, com.priceintel.backend.constants.MatchStatus.REJECTED));
        return response;
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(Long id, UpdateProductRequest request) {
        Product product = findProductOrThrow(id);

        productMapper.updateProductFromRequest(request, product);

        if (request.getCategory() != null) {
            product.setCategory(resolveCategory(request.getCategory()));
        }
        // Replace child collections only when explicitly provided.
        if (request.getIdentifiers() != null) {
            // Supplying the list replaces it wholesale, so the replacement must
            // still carry something searchable — otherwise an edit could silently
            // strip the identifiers every marketplace lookup depends on.
            requireSearchableIdentifier(request.getIdentifiers());
            product.clearIdentifiers();
            applyIdentifiers(product, request.getIdentifiers());
        }
        if (request.getImages() != null) {
            product.clearImages();
            applyImages(product, request.getImages());
        }
        if (request.getAttributes() != null) {
            product.clearAttributes();
            applyAttributes(product, request.getAttributes());
        }

        product = productRepository.save(product);
        log.info("Updated product id={}", id);
        return productMapper.toResponse(product);
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {
        Product product = findProductOrThrow(id);
        productRepository.delete(product); // cascade removes identifiers/images/attributes
        log.info("Deleted product id={}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<ProductResponse> listProducts(String keyword, ProductStatus status, String brand,
                                                       String category, String identifier,
                                                       int page, int size, String sortBy, String direction) {
        return listProducts(keyword, status, brand, category, identifier, null,
                page, size, sortBy, direction);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<ProductResponse> listProducts(String keyword, ProductStatus status, String brand,
                                                       String category, String identifier, Boolean hasCompetitors,
                                                       int page, int size, String sortBy, String direction) {
        Pageable pageable = buildPageable(page, size, sortBy, direction);
        Specification<Product> spec = ProductSpecifications.withFilters(
                keyword, status, brand, category, identifier, hasCompetitors);
        Long tenantId = currentTenantScope();
        if (tenantId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("tenantId"), tenantId));
        }
        Page<ProductResponse> result = productRepository.findAll(spec, pageable)
                .map(productMapper::toResponse);
        applyCompetitorCounts(result.getContent());
        return PagedResponse.from(result);
    }

    /**
     * Fills in {@code competitorCount} for a page of products in one query.
     *
     * <p>Counting per row would issue one query per product to render a single
     * column — the classic way a list page gets slow without anyone noticing
     * until the catalogue grows.</p>
     */
    private void applyCompetitorCounts(List<ProductResponse> products) {
        if (products.isEmpty()) {
            return;
        }
        List<Long> ids = products.stream().map(ProductResponse::getId).filter(java.util.Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return;
        }
        java.util.Map<Long, Long> counts = new java.util.HashMap<>();
        java.util.Map<Long, Long> priced = new java.util.HashMap<>();
        for (Object[] row : listingRepository.countActiveByProductIds(
                ids, com.priceintel.backend.constants.MatchStatus.REJECTED)) {
            counts.put((Long) row[0], (Long) row[1]);
            priced.put((Long) row[0], (Long) row[2]);
        }
        // Absent means no listings at all, which is zero — not unknown.
        products.forEach(p -> {
            p.setCompetitorCount(counts.getOrDefault(p.getId(), 0L));
            p.setPricedCompetitorCount(priced.getOrDefault(p.getId(), 0L));
        });
    }

    @Override
    @Transactional(readOnly = true)
    public ProductFilterOptionsResponse getFilterOptions() {
        Long tenantId = currentTenantScope();
        List<String> brands = tenantId != null
                ? productRepository.findDistinctBrandsByTenant(tenantId)
                : productRepository.findDistinctBrands();
        List<String> categories = tenantId != null
                ? productRepository.findDistinctCategoryNamesByTenant(tenantId)
                : productRepository.findDistinctCategoryNames();
        return ProductFilterOptionsResponse.builder()
                .brands(brands)
                .categories(categories)
                .statuses(Arrays.stream(ProductStatus.values()).map(Enum::name).toList())
                .build();
    }

    // ---------- helpers ----------

    /** The tenant to scope queries to, or null for a super admin (sees all). */
    private Long currentTenantScope() {
        return TenantContext.isSuperAdmin() ? null : TenantContext.getTenantId();
    }

    private Product findProductOrThrow(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        // Tenant isolation: a client can only reach its own products (super admin: all).
        Long tenantId = currentTenantScope();
        if (tenantId != null && !tenantId.equals(product.getTenantId())) {
            throw new ResourceNotFoundException("Product not found with id: " + id);
        }
        return product;
    }

    /** Finds an existing category by name (case-insensitive) or creates it. */
    private ProductCategory resolveCategory(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return categoryRepository.findByNameIgnoreCase(name.trim())
                .orElseGet(() -> categoryRepository.save(
                        ProductCategory.builder().name(name.trim()).build()));
    }

    /**
     * Identifier types that address exactly one real-world product, so one of
     * them may belong to only one product per catalogue.
     *
     * <p>MPN is excluded on purpose: a manufacturer part number is unique only
     * within a brand, so two brands can legitimately use the same string.</p>
     */
    private static final Set<IdentifierType> UNIQUE_IDENTIFIER_TYPES = EnumSet.of(
            IdentifierType.ASIN, IdentifierType.UPC, IdentifierType.EAN, IdentifierType.GTIN);

    /**
     * Identifiers a marketplace lookup can actually be run with.
     *
     * <p>MPN is absent deliberately: no marketplace resolves one, so it is
     * searched as keywords and finds the product only by luck.</p>
     */
    private static final Set<IdentifierType> SEARCHABLE_IDENTIFIER_TYPES = EnumSet.of(
            IdentifierType.ASIN, IdentifierType.MARKETPLACE_ID,
            IdentifierType.UPC, IdentifierType.EAN, IdentifierType.GTIN);

    /**
     * A product needs at least one identifier the platform can search with.
     *
     * <p>Previously this demanded an ASIN, which was right when Amazon was the
     * only marketplace. It is not any more: an eBay listing has an item id and
     * no ASIN and never will, so the rule rejected products that the platform
     * can price perfectly well — and rejected them on <em>edit</em> too, which
     * left eBay products uneditable.</p>
     *
     * <p>What matters is that something can be looked up, not which marketplace
     * it belongs to. A barcode is often the best of the three, since it resolves
     * on Amazon and eBay alike. The case the original rule guarded against — a
     * product with nothing to search, storable but never priceable — is still
     * refused.</p>
     */
    private void requireSearchableIdentifier(List<ProductIdentifierRequest> requests) {
        boolean searchable = requests != null && requests.stream()
                .anyMatch(r -> SEARCHABLE_IDENTIFIER_TYPES.contains(r.getType())
                        && r.getOriginalValue() != null && !r.getOriginalValue().isBlank());
        if (!searchable) {
            throw new BadRequestException(
                    "A product needs at least one identifier to search with: an ASIN, "
                            + "an eBay item id, or a barcode (UPC, EAN or GTIN). "
                            + "MPN alone is not enough — no marketplace can look one up.");
        }
    }

    private void applyIdentifiers(Product product, List<ProductIdentifierRequest> requests) {
        if (requests == null) {
            return;
        }
        Set<String> seenInThisRequest = new HashSet<>();
        for (ProductIdentifierRequest req : requests) {
            String normalized = IdentifierNormalizer.normalize(req.getType(), req.getOriginalValue());
            // Structural check before the uniqueness one: "that barcode has a
            // typo" is a more useful answer than "that barcode is taken".
            com.priceintel.backend.utils.IdentifierValidator.validate(req.getType(), normalized)
                    .ifPresent(reason -> {
                        throw new BadRequestException(reason);
                    });
            assertIdentifierIsFree(product, req.getType(), normalized, seenInThisRequest);
            ProductIdentifier identifier = ProductIdentifier.builder()
                    .type(req.getType())
                    .originalValue(req.getOriginalValue())
                    .normalizedValue(normalized)
                    .marketplace(req.getMarketplace())
                    // Carries the product's tenant so the unique index can apply.
                    .tenantId(product.getTenantId() != null
                            ? product.getTenantId() : TenantContext.getTenantId())
                    .build();
            product.addIdentifier(identifier);
        }
    }

    /**
     * Rejects an identifier already used by another product in this tenant.
     *
     * <p>An ASIN names one Amazon listing; letting two products claim it means
     * at least one is mislabelled, and both would then pull the same competitor
     * research — wrong prices attached to the wrong product. The check is scoped
     * to the tenant, so two clients stocking the same item are unaffected.</p>
     */
    private void assertIdentifierIsFree(Product product, IdentifierType type, String normalized,
                                        Set<String> seenInThisRequest) {
        if (!UNIQUE_IDENTIFIER_TYPES.contains(type) || normalized == null || normalized.isBlank()) {
            return;
        }
        if (!seenInThisRequest.add(type + ":" + normalized)) {
            throw new DuplicateResourceException(
                    type + " " + normalized + " is listed twice on this product.");
        }
        Long tenantId = product.getTenantId() != null ? product.getTenantId() : TenantContext.getTenantId();
        if (tenantId == null) {
            return; // platform-level context: nothing to scope the rule to
        }
        // A new product has no id yet; -1 can never match a real row.
        Long excludeId = product.getId() != null ? product.getId() : -1L;
        List<ProductIdentifier> conflicts =
                identifierRepository.findConflicts(type, normalized, tenantId, excludeId);
        if (!conflicts.isEmpty()) {
            Product owner = conflicts.get(0).getProduct();
            throw new DuplicateResourceException(
                    type + " " + normalized + " is already assigned to product \""
                            + owner.getTitle() + "\" (SKU " + owner.getSku()
                            + "). One " + type + " can belong to only one product.");
        }
    }

    private void applyImages(Product product, List<ProductImageRequest> requests) {
        if (requests == null) {
            return;
        }
        for (ProductImageRequest req : requests) {
            // Compute a perceptual hash for image search when one isn't supplied.
            String hash = req.getHash();
            if (hash == null || hash.isBlank()) {
                hash = imageHashService.hash(req.getUrl()).orElse(null);
            }
            ProductImage image = ProductImage.builder()
                    .url(req.getUrl())
                    .hash(hash)
                    .status(req.getStatus() != null ? req.getStatus() : ImageStatus.PENDING)
                    .build();
            product.addImage(image);
        }
    }

    private void applyAttributes(Product product, List<ProductAttributeRequest> requests) {
        if (requests == null) {
            return;
        }
        for (ProductAttributeRequest req : requests) {
            ProductAttribute attribute = ProductAttribute.builder()
                    .color(req.getColor())
                    .size(req.getSize())
                    .material(req.getMaterial())
                    .model(req.getModel())
                    .variant(req.getVariant())
                    .country(req.getCountry())
                    .category(req.getCategory())
                    .build();
            product.addAttribute(attribute);
        }
    }

    private Pageable buildPageable(int page, int size, String sortBy, String direction) {
        if (page < 0) {
            throw new BadRequestException("Page index must not be negative");
        }
        if (size < 1 || size > 100) {
            throw new BadRequestException("Page size must be between 1 and 100");
        }
        String sortField = SORTABLE_FIELDS.contains(sortBy) ? sortBy : "createdAt";
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(dir, sortField));
    }
}
