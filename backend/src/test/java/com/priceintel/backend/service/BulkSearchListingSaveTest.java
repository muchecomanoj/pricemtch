package com.priceintel.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceintel.backend.constants.IdentifierType;
import com.priceintel.backend.dto.request.CreateProductRequest;
import com.priceintel.backend.dto.request.ProductIdentifierRequest;
import com.priceintel.backend.dto.response.ProductResponse;
import com.priceintel.backend.entity.BulkSearchJob;
import com.priceintel.backend.entity.BulkSearchRow;
import com.priceintel.backend.entity.BulkSearchRowSave;
import com.priceintel.backend.entity.Product;
import com.priceintel.backend.exception.BadRequestException;
import com.priceintel.backend.repository.BulkSearchJobRepository;
import com.priceintel.backend.repository.BulkSearchRowRepository;
import com.priceintel.backend.repository.BulkSearchRowSaveRepository;
import com.priceintel.backend.repository.ProductIdentifierRepository;
import com.priceintel.backend.repository.ProductRepository;
import com.priceintel.backend.service.ProductService;
import com.priceintel.backend.service.impl.BulkSearchService;

/**
 * Saving one matched listing from a bulk-search row.
 *
 * <p>A row matches several listings and each can now be saved on its own, so one
 * row produces one product per listing saved. The rules worth protecting are
 * that the right listing's values are used, that a second click on the same card
 * is harmless, and that a listing from somewhere else cannot be saved against
 * this row.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BulkSearchListingSaveTest {

    @Mock BulkSearchJobRepository jobRepo;
    @Mock BulkSearchRowRepository rowRepo;
    @Mock BulkSearchRowSaveRepository rowSaveRepo;
    @Mock ProductRepository productRepo;
    @Mock ProductIdentifierRepository identifierRepo;
    @Mock ProductService productService;
    @Spy ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks BulkSearchService service;

    /** Two colour variants of one product, on two marketplaces. */
    private static final String MATCHES = """
            [
              { "marketplace": "AMAZON", "marketplaceItemId": "B07YXM6XTF",
                "title": "Hydro Flask Water Bottle 32 Oz Black",
                "seller": "Hydro Flask", "price": 43.93,
                "identifiers": [ { "type": "ASIN", "value": "B07YXM6XTF" },
                                 { "type": "EAN",  "value": "745453926322" },
                                 { "type": "EAN",  "value": "745453926339" } ] },
              { "marketplace": "EBAY", "marketplaceItemId": "147262115774",
                "title": "Hydro Flask 32 oz Wide Mouth Pacific Blue",
                "seller": "shopcelldeals", "price": 39.95,
                "identifiers": [ { "type": "EAN", "value": "810096859235" } ] }
            ]
            """;

    private final List<BulkSearchRowSave> savedRows = new ArrayList<>();
    private final java.util.Map<Long, Product> created = new java.util.HashMap<>();
    private final AtomicLong nextProductId = new AtomicLong(100);

    @BeforeEach
    void setUp() {
        BulkSearchJob job = new BulkSearchJob();
        job.setId(5L);
        when(jobRepo.findById(5L)).thenReturn(Optional.of(job));
        when(identifierRepo.findByTypeAndNormalizedValue(any(), any())).thenReturn(List.of());

        // Products created during a test are findable afterwards, as they would
        // be in the database. Without this the "same listing twice" guard looks
        // up the product it just made, finds nothing, and makes another.
        when(productService.createProduct(any())).thenAnswer(i -> {
            var req = (CreateProductRequest) i.getArgument(0);
            long id = nextProductId.getAndIncrement();
            Product stored = new Product();
            stored.setId(id);
            stored.setSku(req.getSku());
            stored.setTitle(req.getTitle());
            created.put(id, stored);
            return ProductResponse.builder().id(id).sku(req.getSku()).title(req.getTitle()).build();
        });
        when(productRepo.findById(any())).thenAnswer(
                i -> Optional.ofNullable(created.get((Long) i.getArgument(0))));

        // A tiny stand-in for the saves table, so "same card twice" is a real test.
        when(rowSaveRepo.save(any())).thenAnswer(i -> {
            savedRows.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(rowSaveRepo.findByRowIdAndMarketplaceIgnoreCaseAndMarketplaceItemId(any(), any(), any()))
                .thenAnswer(i -> savedRows.stream()
                        .filter(s -> s.getRowId().equals(i.getArgument(0))
                                && s.getMarketplace().equalsIgnoreCase(i.getArgument(1))
                                && s.getMarketplaceItemId().equals(i.getArgument(2)))
                        .findFirst());
    }

    private BulkSearchRow row() {
        BulkSearchRow r = new BulkSearchRow();
        r.setId(7L);
        r.setJobId(5L);
        r.setInputTitle("Hydro Flask 32 oz Wide Mouth Insulated Water Bottle");
        r.setInputBrand("Hydro Flask");
        r.setMatchesJson(MATCHES);
        when(rowRepo.findById(7L)).thenReturn(Optional.of(r));
        return r;
    }

    private CreateProductRequest lastCreated() {
        ArgumentCaptor<CreateProductRequest> c = ArgumentCaptor.forClass(CreateProductRequest.class);
        verify(productService, org.mockito.Mockito.atLeastOnce()).createProduct(c.capture());
        return c.getValue();
    }

    @Test
    @DisplayName("two different listings from one row create two products")
    void twoListingsTwoProducts() {
        row();

        var first = service.saveToCatalogue(5L, 7L, null, false, "AMAZON", "B07YXM6XTF");
        var second = service.saveToCatalogue(5L, 7L, null, false, "EBAY", "147262115774");

        assertThat(first.getStatus()).isEqualTo("CREATED");
        assertThat(second.getStatus()).isEqualTo("CREATED");
        // The row-level "already saved by this row" shortcut must not fire here,
        // or the second card would answer with the first card's product.
        assertThat(second.getProductId()).isNotEqualTo(first.getProductId());
        verify(productService, org.mockito.Mockito.times(2)).createProduct(any());
    }

    @Test
    @DisplayName("saving the same listing twice creates one product")
    void sameListingTwiceIsOneProduct() {
        row();

        var first = service.saveToCatalogue(5L, 7L, null, false, "AMAZON", "B07YXM6XTF");
        var again = service.saveToCatalogue(5L, 7L, null, false, "AMAZON", "B07YXM6XTF");

        assertThat(again.getProductId()).isEqualTo(first.getProductId());
        verify(productService, org.mockito.Mockito.times(1)).createProduct(any());
    }

    @Test
    @DisplayName("the chosen listing's own values are used, not the row's or another card's")
    void usesTheChosenListingsValues() {
        row();

        service.saveToCatalogue(5L, 7L, null, false, "EBAY", "147262115774");
        CreateProductRequest created = lastCreated();

        assertThat(created.getTitle()).isEqualTo("Hydro Flask 32 oz Wide Mouth Pacific Blue");
        assertThat(created.getIdentifiers())
                .extracting(ProductIdentifierRequest::getOriginalValue)
                .contains("810096859235", "147262115774")
                .doesNotContain("745453926322");
    }

    @Test
    @DisplayName("on Amazon the seller field is the brand; on eBay it is a merchant, so it is left out")
    void brandOnlyFromAmazon() {
        row();

        service.saveToCatalogue(5L, 7L, null, false, "AMAZON", "B07YXM6XTF");
        assertThat(lastCreated().getBrand()).isEqualTo("Hydro Flask");

        service.saveToCatalogue(5L, 7L, null, false, "EBAY", "147262115774");
        assertThat(lastCreated().getBrand()).isNull();
    }

    @Test
    @DisplayName("an Amazon listing id is stored as the ASIN; any other as a marketplace id")
    void listingIdIsTypedCorrectly() {
        row();

        service.saveToCatalogue(5L, 7L, null, false, "AMAZON", "B07YXM6XTF");
        assertThat(lastCreated().getIdentifiers())
                .extracting(ProductIdentifierRequest::getType, ProductIdentifierRequest::getOriginalValue)
                .contains(org.assertj.core.groups.Tuple.tuple(IdentifierType.ASIN, "B07YXM6XTF"));

        service.saveToCatalogue(5L, 7L, null, false, "EBAY", "147262115774");
        assertThat(lastCreated().getIdentifiers())
                .extracting(ProductIdentifierRequest::getType, ProductIdentifierRequest::getOriginalValue)
                .contains(org.assertj.core.groups.Tuple.tuple(
                        IdentifierType.MARKETPLACE_ID, "147262115774"));
    }

    @Test
    @DisplayName("the SKU falls back to the listing's ASIN when none is typed")
    void skuFallsBackToTheListing() {
        row();

        var response = service.saveToCatalogue(5L, 7L, null, false, "AMAZON", "B07YXM6XTF");

        assertThat(response.getProductSku()).isEqualTo("B07YXM6XTF");
    }

    @Test
    @DisplayName("a listing that is not one of this row's matches is refused")
    void foreignListingIsRefused() {
        row();

        assertThatThrownBy(() -> service.saveToCatalogue(5L, 7L, null, false, "AMAZON", "B000NOTMINE"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("isn't one of this row's matches");

        verify(productService, never()).createProduct(any());
    }

    @Test
    @DisplayName("the answer names the listing it is about, so a card can match its own reply")
    void responseEchoesTheListing() {
        row();

        var response = service.saveToCatalogue(5L, 7L, null, false, "EBAY", "147262115774");

        assertThat(response.getMarketplace()).isEqualTo("EBAY");
        assertThat(response.getMarketplaceItemId()).isEqualTo("147262115774");
    }

    @Test
    @DisplayName("a listing already in the catalogue comes back as EXISTS, not a duplicate")
    void existingProductIsReported() {
        row();
        Product existing = new Product();
        existing.setId(42L);
        existing.setSku("OLD-SKU");
        existing.setTitle("Hydro Flask (old title)");
        var stored = new com.priceintel.backend.entity.ProductIdentifier();
        stored.setType(IdentifierType.ASIN);
        stored.setNormalizedValue("B07YXM6XTF");
        stored.setOriginalValue("B07YXM6XTF");
        stored.setProduct(existing);
        when(identifierRepo.findByTypeAndNormalizedValue(IdentifierType.ASIN, "B07YXM6XTF"))
                .thenReturn(List.of(stored));
        when(identifierRepo.findByProductId(42L)).thenReturn(List.of(stored));

        var response = service.saveToCatalogue(5L, 7L, null, false, "AMAZON", "B07YXM6XTF");

        assertThat(response.getStatus()).isEqualTo("EXISTS");
        assertThat(response.getProductId()).isEqualTo(42L);
        assertThat(response.getMatchedOn()).contains("B07YXM6XTF");
        // update=false must never write.
        verify(productService, never()).updateProduct(any(), any());
        verify(productService, never()).createProduct(any());
    }

    @Test
    @DisplayName("update=true applies the listing's values, not the row's")
    void updateAppliesTheListingsValues() {
        row();
        Product existing = new Product();
        existing.setId(42L);
        existing.setSku("OLD-SKU");
        existing.setTitle("Hydro Flask (old title)");
        existing.setOurPrice(new BigDecimal("10.00"));
        var stored = new com.priceintel.backend.entity.ProductIdentifier();
        stored.setType(IdentifierType.ASIN);
        stored.setNormalizedValue("B07YXM6XTF");
        stored.setOriginalValue("B07YXM6XTF");
        stored.setProduct(existing);
        when(identifierRepo.findByTypeAndNormalizedValue(IdentifierType.ASIN, "B07YXM6XTF"))
                .thenReturn(List.of(stored));
        when(identifierRepo.findByProductId(42L)).thenReturn(List.of(stored));
        when(productService.updateProduct(any(), any()))
                .thenReturn(ProductResponse.builder().id(42L).sku("OLD-SKU")
                        .title("Hydro Flask Water Bottle 32 Oz Black").build());

        var response = service.saveToCatalogue(5L, 7L, null, true, "AMAZON", "B07YXM6XTF");

        assertThat(response.getStatus()).isEqualTo("UPDATED");
        var req = ArgumentCaptor.forClass(com.priceintel.backend.dto.request.UpdateProductRequest.class);
        verify(productService).updateProduct(org.mockito.ArgumentMatchers.eq(42L), req.capture());
        // The Amazon listing's title and price, not the spreadsheet's.
        assertThat(req.getValue().getTitle()).isEqualTo("Hydro Flask Water Bottle 32 Oz Black");
        assertThat(req.getValue().getOurPrice()).isEqualByComparingTo("43.93");
    }

    @Test
    @DisplayName("calling without the new parameters behaves exactly as before")
    void rowModeUnchanged() {
        BulkSearchRow r = row();
        r.setInputSku("BS-HYDRO-32");
        r.setInputAsin("B00FROMFILE");

        var response = service.saveToCatalogue(5L, 7L, null, false);

        assertThat(response.getProductSku()).isEqualTo("BS-HYDRO-32");
        // The row's own title and identifier, never a listing's.
        assertThat(lastCreated().getTitle())
                .isEqualTo("Hydro Flask 32 oz Wide Mouth Insulated Water Bottle");
        assertThat(lastCreated().getIdentifiers())
                .extracting(ProductIdentifierRequest::getOriginalValue)
                .containsExactly("B00FROMFILE");
        assertThat(response.getMarketplace()).isNull();
        assertThat(r.getSavedProductId()).isNotNull();
        // Row-mode saves are not listing saves, so nothing is recorded per card.
        assertThat(savedRows).isEmpty();
    }
}
