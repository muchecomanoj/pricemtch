package com.priceintel.backend.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceintel.backend.constants.IdentifierType;
import com.priceintel.backend.dto.request.SearchRequest;
import com.priceintel.backend.entity.Product;
import com.priceintel.backend.mapper.ProductMapper;
import com.priceintel.backend.mapper.SearchMapper;
import com.priceintel.backend.repository.ProductIdentifierRepository;
import com.priceintel.backend.repository.ProductImageRepository;
import com.priceintel.backend.repository.ProductRepository;
import com.priceintel.backend.repository.SearchHistoryRepository;
import com.priceintel.backend.security.TenantContext;
import com.priceintel.backend.service.impl.SearchServiceImpl;

/**
 * "In your catalogue" must mean this company's catalogue.
 *
 * <p>Seen live: Prativa (Patuli Infosys) searched "Apple AirPods 4" and was
 * shown Price Matrix's product, SKU TEST-AIRPODS-4, as her own.</p>
 */
class CatalogueSearchIsolationTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final ProductIdentifierRepository identifiers = mock(ProductIdentifierRepository.class);
    private final ProductImageRepository images = mock(ProductImageRepository.class);
    private final SearchServiceImpl search = new SearchServiceImpl(products, identifiers, images,
            mock(SearchHistoryRepository.class), mock(ProductMapper.class), mock(SearchMapper.class),
            new ObjectMapper());

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private static SearchRequest query(String q) {
        SearchRequest r = new SearchRequest();
        r.setQuery(q);
        return r;
    }

    @Test
    @DisplayName("a company's title search looks only in its own catalogue")
    void titleSearchIsScoped() {
        TenantContext.set(72L, false);   // Patuli Infosys

        search.search(query("Apple AirPods 4"));

        verify(products).findByTenantIdAndTitleContainingIgnoreCase(eq(72L), eq("Apple AirPods 4"), any());
        verify(products, never()).findByTitleContainingIgnoreCase(anyString(), any());
    }

    @Test
    @DisplayName("identifier, SKU and image-address searches are scoped too")
    void everyStageIsScoped() {
        TenantContext.set(72L, false);

        search.search(query("B0DGJ7HYG1"));

        verify(identifiers).findByTenantIdAndTypeAndNormalizedValue(72L, IdentifierType.ASIN, "B0DGJ7HYG1");
        verify(identifiers, never()).findByTypeAndNormalizedValue(any(), any());
        verify(products).findByTenantIdAndSkuIgnoreCase(72L, "B0DGJ7HYG1");
        verify(products, never()).findBySkuIgnoreCase(anyString());
        verify(images).findProductsByTenantAndImageUrlContaining(eq(72L), eq("B0DGJ7HYG1"), any());
        verify(images, never()).findProductsByImageUrlContaining(anyString(), any());
    }

    @Test
    @DisplayName("the platform owner, who has no company, still searches everything")
    void superAdminSearchesAll() {
        TenantContext.set(null, true);

        search.search(query("Apple AirPods 4"));

        verify(products).findByTitleContainingIgnoreCase(eq("Apple AirPods 4"), any());
    }

    @Test
    @DisplayName("no company and not the owner: nothing, rather than everything")
    void noCompanyFindsNothing() {
        TenantContext.set(null, false);
        when(products.findByTitleContainingIgnoreCase(anyString(), any())).thenReturn(List.of(new Product()));

        var result = search.search(query("Apple AirPods 4"));

        assertThat(result.getResults()).isEmpty();
        verify(products, never()).findByTitleContainingIgnoreCase(anyString(), any());
    }
}
