package com.priceintel.backend.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.priceintel.backend.constants.IdentifierType;
import com.priceintel.backend.constants.ProductCondition;
import com.priceintel.backend.dto.request.CreateProductRequest;
import com.priceintel.backend.dto.request.ProductIdentifierRequest;
import com.priceintel.backend.entity.Product;
import com.priceintel.backend.entity.ProductIdentifier;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.exception.DuplicateResourceException;
import com.priceintel.backend.mapper.ProductMapper;
import com.priceintel.backend.repository.ProductCategoryRepository;
import com.priceintel.backend.repository.ProductIdentifierRepository;
import com.priceintel.backend.repository.ProductRepository;
import com.priceintel.backend.security.TenantContext;
import com.priceintel.backend.service.impl.ProductServiceImpl;
import com.priceintel.backend.utils.ImageHashService;

/**
 * One marketplace identifier belongs to one product per client.
 *
 * <p>A single ASIN names one Amazon listing. Two products claiming it means one
 * is mislabelled, and both would then adopt the same competitor research —
 * attaching another product's prices to yours. The rule is scoped per tenant,
 * because two clients stocking the same item legitimately both hold its ASIN.</p>
 */
class IdentifierUniquenessTest {

    private ProductRepository productRepository;
    private ProductIdentifierRepository identifierRepository;
    private ProductServiceImpl service;

    private static final Long TENANT = 62L;
    private static final String ASIN = "B08L5NP6NG";

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        identifierRepository = mock(ProductIdentifierRepository.class);
        service = new ProductServiceImpl(
                productRepository,
                mock(ProductCategoryRepository.class),
                identifierRepository,
                mock(ProductMapper.class),
                mock(ImageHashService.class),
                mock(com.priceintel.backend.repository.CompetitorListingRepository.class));

        when(productRepository.existsByTenantIdAndSkuIgnoreCase(any(), any())).thenReturn(false);
        when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // Default: identifier is free.
        when(identifierRepository.findConflicts(any(), any(), anyLong(), anyLong()))
                .thenReturn(List.of());
        TenantContext.set(TENANT, false);
    }

    @Test
    void aSecondProductCannotClaimAnAsinAlreadyUsedInTheSameCatalogue() {
        givenAsinBelongsTo("Apple MagSafe Charger", "APP-MAGSAFE-001");

        assertThatThrownBy(() -> service.createProduct(productWith(ASIN)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining(ASIN)
                .hasMessageContaining("Apple MagSafe Charger")
                .hasMessageContaining("APP-MAGSAFE-001");
    }

    @Test
    void theErrorNamesTheOwningProductSoTheUserCanFixIt() {
        givenAsinBelongsTo("Apple MagSafe Charger", "APP-MAGSAFE-001");

        assertThatThrownBy(() -> service.createProduct(productWith(ASIN)))
                .hasMessageContaining("only one product");
    }

    @Test
    void anUnusedAsinIsAccepted() {
        assertThatCode(() -> service.createProduct(productWith(ASIN))).doesNotThrowAnyException();
    }

    @Test
    void theSameAsinListedTwiceOnOneProductIsRejected() {
        CreateProductRequest request = CreateProductRequest.builder()
                .sku("DUP-1").title("Duplicate identifiers")
                .identifiers(List.of(identifier(ASIN), identifier(ASIN)))
                .build();

        assertThatThrownBy(() -> service.createProduct(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("twice");
    }

    @Test
    /**
     * A structurally valid value for each type, so a uniqueness test fails on
     * uniqueness rather than on a malformed check digit.
     */
    private String validValueFor(IdentifierType type) {
        return switch (type) {
            case UPC -> "036000291452";
            case EAN, GTIN -> "4006381333931";
            default -> "B0VALIDASN";
        };
    }

    @Test
    void manufacturerPartNumbersAreNotForcedUnique() {
        // An MPN is unique only within a brand, so two brands may share one.
        // A searchable identifier is still required alongside it.
        CreateProductRequest request = CreateProductRequest.builder()
                .sku("MPN-1").title("Generic cable")
                .identifiers(List.of(
                        identifier("B0SOMEASIN"),
                        ProductIdentifierRequest.builder()
                                .type(IdentifierType.MPN).originalValue("1A6421Y01").build()))
                .build();

        assertThatCode(() -> service.createProduct(request)).doesNotThrowAnyException();
    }

    // ---------- something to search with is mandatory ----------

    @Test
    void aBarcodeIsEnoughWithoutAnAsin() {
        // A barcode resolves on Amazon and eBay alike, so it is at least as
        // useful as an ASIN for finding the product.
        CreateProductRequest request = CreateProductRequest.builder()
                .sku("UPC-ONLY").title("Barcode only")
                .identifiers(List.of(ProductIdentifierRequest.builder()
                        .type(IdentifierType.UPC).originalValue("012345678905").build()))
                .build();

        assertThatCode(() -> service.createProduct(request)).doesNotThrowAnyException();
    }

    @Test
    void anEbayItemIdIsEnoughWithoutAnAsin() {
        // The case that reached the client: a product added from an eBay listing
        // has an item id and no ASIN, and never will.
        CreateProductRequest request = CreateProductRequest.builder()
                .sku("EBAY-ONLY").title("Ninja Nutri-Plus Personal Blender")
                .identifiers(List.of(ProductIdentifierRequest.builder()
                        .type(IdentifierType.MARKETPLACE_ID)
                        .originalValue("v1|147569478540|0").marketplace("EBAY").build()))
                .build();

        assertThatCode(() -> service.createProduct(request)).doesNotThrowAnyException();
    }

    @Test
    void anEbayOnlyProductCanStillBeEdited() {
        // The old rule ran on update too, which left eBay products uneditable:
        // saving the edit re-sent identifiers that carried no ASIN.
        com.priceintel.backend.security.TenantContext.clear();
        Product existing = Product.builder().sku("EBAY-EDIT").title("eBay product").build();
        existing.setId(42L);
        when(productRepository.findById(42L)).thenReturn(java.util.Optional.of(existing));

        com.priceintel.backend.dto.request.UpdateProductRequest update =
                com.priceintel.backend.dto.request.UpdateProductRequest.builder()
                        .title("eBay product, renamed")
                        .identifiers(List.of(ProductIdentifierRequest.builder()
                                .type(IdentifierType.MARKETPLACE_ID)
                                .originalValue("v1|147569478541|0").marketplace("EBAY").build()))
                        .build();

        assertThatCode(() -> service.updateProduct(42L, update)).doesNotThrowAnyException();
    }

    @Test
    void anMpnAloneIsRejectedBecauseNothingCanLookItUp() {
        CreateProductRequest request = CreateProductRequest.builder()
                .sku("MPN-ONLY").title("Part number only")
                .identifiers(List.of(ProductIdentifierRequest.builder()
                        .type(IdentifierType.MPN).originalValue("1A6421Y01").build()))
                .build();

        assertThatThrownBy(() -> service.createProduct(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("at least one identifier to search with");
    }

    @Test
    void omittingIdentifiersEntirelyIsRejected() {
        CreateProductRequest request = CreateProductRequest.builder()
                .sku("NONE").title("No identifiers at all").build();

        assertThatThrownBy(() -> service.createProduct(request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void aBlankIdentifierDoesNotCountAsProvided() {
        CreateProductRequest request = CreateProductRequest.builder()
                .sku("BLANK").title("Blank ASIN")
                .identifiers(List.of(ProductIdentifierRequest.builder()
                        .type(IdentifierType.ASIN).originalValue("   ").build()))
                .build();

        assertThatThrownBy(() -> service.createProduct(request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void theOtherIdentifierTypesRemainOptional() {
        CreateProductRequest request = CreateProductRequest.builder()
                .sku("ASIN-ONLY").title("ASIN only")
                .identifiers(List.of(identifier("B0ASINONLY")))
                .build();

        assertThatCode(() -> service.createProduct(request)).doesNotThrowAnyException();
    }

    // ---------- condition ----------

    @Test
    void conditionDefaultsToNewWhenNotSupplied() {
        service.createProduct(productWith(ASIN));

        org.mockito.ArgumentCaptor<Product> saved =
                org.mockito.ArgumentCaptor.forClass(Product.class);
        org.mockito.Mockito.verify(productRepository).save(saved.capture());
        assertThat(saved.getValue().getCondition()).isEqualTo(ProductCondition.NEW);
    }

    @Test
    void conditionIsPersistedWhenSupplied() {
        CreateProductRequest request = CreateProductRequest.builder()
                .sku("USED-1").title("A used unit")
                .condition(ProductCondition.REFURBISHED)
                .identifiers(List.of(identifier("B0REFURB01")))
                .build();

        service.createProduct(request);

        org.mockito.ArgumentCaptor<Product> saved =
                org.mockito.ArgumentCaptor.forClass(Product.class);
        org.mockito.Mockito.verify(productRepository).save(saved.capture());
        assertThat(saved.getValue().getCondition()).isEqualTo(ProductCondition.REFURBISHED);
    }

    @Test
    void everyGloballyUniqueIdentifierTypeIsCovered() {
        for (IdentifierType type : List.of(IdentifierType.ASIN, IdentifierType.UPC,
                IdentifierType.EAN, IdentifierType.GTIN)) {
            // Only the type under test collides; everything else is free, so the
            // failure can only come from that type's uniqueness rule.
            org.mockito.Mockito.reset(identifierRepository);
            when(identifierRepository.findConflicts(any(), any(), anyLong(), anyLong()))
                    .thenReturn(List.of());
            when(identifierRepository.findConflicts(eq(type), any(), anyLong(), anyLong()))
                    .thenReturn(List.of(existingIdentifier("Other product", "SKU-X")));

            // Every product needs an ASIN, so non-ASIN cases carry a clean one
            // alongside the identifier actually being tested.
            List<ProductIdentifierRequest> identifiers = type == IdentifierType.ASIN
                    ? List.of(identifier("B0COLLIDNG"))
                    : List.of(identifier("B0CLEANASN"),
                            ProductIdentifierRequest.builder()
                                    .type(type).originalValue(validValueFor(type)).build());

            CreateProductRequest request = CreateProductRequest.builder()
                    .sku("X-" + type).title("Attempt " + type)
                    .identifiers(identifiers)
                    .build();

            assertThatThrownBy(() -> service.createProduct(request))
                    .describedAs("%s must be unique per tenant", type)
                    .isInstanceOf(DuplicateResourceException.class);
        }
    }

    @Test
    void theCheckIsScopedToTheTenantThatOwnsTheProduct() {
        service.createProduct(productWith(ASIN));

        // Whatever the conflict query is asked, it must carry this tenant.
        org.mockito.ArgumentCaptor<Long> tenant = org.mockito.ArgumentCaptor.forClass(Long.class);
        org.mockito.Mockito.verify(identifierRepository)
                .findConflicts(any(), any(), tenant.capture(), anyLong());
        assertThat(tenant.getValue()).isEqualTo(TENANT);
    }

    // ---------- helpers ----------

    private void givenAsinBelongsTo(String title, String sku) {
        when(identifierRepository.findConflicts(eq(IdentifierType.ASIN), eq(ASIN), anyLong(), anyLong()))
                .thenReturn(List.of(existingIdentifier(title, sku)));
    }

    private ProductIdentifier existingIdentifier(String title, String sku) {
        Product owner = Product.builder().sku(sku).title(title).tenantId(TENANT).build();
        owner.setId(42L);
        return ProductIdentifier.builder()
                .type(IdentifierType.ASIN).originalValue(ASIN).normalizedValue(ASIN)
                .product(owner).build();
    }

    // ---- SKU: unique within a company, not across the platform ----

    @Test
    void aSkuIsCheckedOnlyAgainstThisCompanysProducts() {
        // Another company already using "NEW-SKU" is invisible here: the check
        // is asked about this company alone, so creating the product succeeds.
        assertThatCode(() -> service.createProduct(productWith(ASIN))).doesNotThrowAnyException();
        org.mockito.Mockito.verify(productRepository).existsByTenantIdAndSkuIgnoreCase(TENANT, "NEW-SKU");
    }

    @Test
    void aSkuAlreadyUsedInTheSameCompanyIsRefusedWithoutMentioningOthers() {
        when(productRepository.existsByTenantIdAndSkuIgnoreCase(TENANT, "NEW-SKU")).thenReturn(true);

        assertThatThrownBy(() -> service.createProduct(productWith(ASIN)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("You already have a product with SKU NEW-SKU");
    }

    private CreateProductRequest productWith(String asin) {
        return CreateProductRequest.builder()
                .sku("NEW-SKU").title("New product")
                .identifiers(List.of(identifier(asin)))
                .build();
    }

    private ProductIdentifierRequest identifier(String asin) {
        return ProductIdentifierRequest.builder()
                .type(IdentifierType.ASIN).originalValue(asin).build();
    }
}
