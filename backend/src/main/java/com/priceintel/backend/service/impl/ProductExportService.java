package com.priceintel.backend.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.priceintel.backend.constants.ProductStatus;
import com.priceintel.backend.entity.Product;
import com.priceintel.backend.entity.ProductIdentifier;
import com.priceintel.backend.repository.ProductRepository;
import com.priceintel.backend.repository.ProductSpecifications;
import com.priceintel.backend.security.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Exports the product catalogue as CSV (FR-PROD-005).
 *
 * <p>The column headers are exactly the ones the importer accepts, so an export
 * can be edited and uploaded straight back. A catalogue you can only read out of
 * the system is half a feature: bulk edits are the reason people ask for
 * export.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductExportService {

    /**
     * Import-compatible headers, in the order a person reads them: what the
     * product is, then how it is identified, then its physical facts.
     */
    private static final String[] HEADERS = {
        "SKU", "Title", "Brand", "Category", "Status", "Condition",
        "ASIN", "UPC", "EAN", "GTIN", "MPN",
        "Pack Quantity", "Weight", "Length", "Width", "Height",
        "Description",
    };

    private final ProductRepository productRepository;

    /**
     * Exports every product matching the same filters as the list screen, so
     * "Export" gives you what you are looking at rather than the whole
     * catalogue.
     */
    @Transactional(readOnly = true)
    public byte[] exportCsv(String keyword, ProductStatus status, String brand,
            String category, String identifier) {
        Specification<Product> spec =
                ProductSpecifications.withFilters(keyword, status, brand, category, identifier);
        Long tenantId = TenantContext.scopeOrAllForSuperAdmin();
        if (tenantId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("tenantId"), tenantId));
        }
        List<Product> products = productRepository.findAll(spec, Sort.by("sku"));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // The BOM is what makes Excel open this as UTF-8; without it, accented
        // brand and product names arrive mangled.
        try {
            out.write(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        try (CSVPrinter printer = new CSVPrinter(
                new OutputStreamWriter(out, StandardCharsets.UTF_8),
                CSVFormat.DEFAULT.builder().setHeader(HEADERS).build())) {

            for (Product p : products) {
                Map<String, String> ids = identifiersOf(p);
                printer.printRecord(
                        p.getSku(),
                        p.getTitle(),
                        p.getBrand(),
                        p.getCategory() != null ? p.getCategory().getName() : null,
                        p.getStatus() != null ? p.getStatus().name() : null,
                        p.getCondition() != null ? p.getCondition().name() : null,
                        ids.get("ASIN"),
                        ids.get("UPC"),
                        ids.get("EAN"),
                        ids.get("GTIN"),
                        ids.get("MPN"),
                        p.getPackQuantity(),
                        p.getWeight(),
                        p.getLength(),
                        p.getWidth(),
                        p.getHeight(),
                        p.getDescription());
            }
            printer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        log.info("Exported {} product(s) for tenant {}", products.size(), tenantId);
        return out.toByteArray();
    }

    private Map<String, String> identifiersOf(Product p) {
        if (p.getIdentifiers() == null) {
            return Map.of();
        }
        return p.getIdentifiers().stream()
                .filter(i -> i.getType() != null && i.getOriginalValue() != null)
                .collect(Collectors.toMap(
                        i -> i.getType().name(),
                        ProductIdentifier::getOriginalValue,
                        // A product should hold one of each type, but a duplicate
                        // must not abort the whole export.
                        (first, second) -> first));
    }
}
