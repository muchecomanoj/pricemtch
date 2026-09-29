package com.priceintel.backend.utils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.priceintel.backend.constants.IdentifierType;
import com.priceintel.backend.constants.ImportFieldCatalog;
import com.priceintel.backend.constants.ProductStatus;
import com.priceintel.backend.dto.request.CreateProductRequest;
import com.priceintel.backend.dto.request.ProductAttributeRequest;
import com.priceintel.backend.dto.request.ProductIdentifierRequest;
import com.priceintel.backend.dto.request.ProductImageRequest;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Converts one raw import row (header-&gt;value) into a {@link CreateProductRequest},
 * collecting per-field validation errors instead of throwing. This keeps the
 * import resilient: a bad row is reported, not fatal.
 */
public final class ImportRowConverter {

    private ImportRowConverter() {
    }

    @Getter
    @AllArgsConstructor
    public static class FieldError {
        private final String field;
        private final String message;
    }

    @Getter
    @AllArgsConstructor
    public static class Result {
        private final CreateProductRequest request; // null if there are blocking errors
        private final List<FieldError> errors;

        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    /**
     * @param row     header-&gt;value for one data row
     * @param mapping fieldKey-&gt;header (resolved column mapping)
     */
    public static Result convert(Map<String, String> row, Map<String, String> mapping) {
        List<FieldError> errors = new ArrayList<>();

        String sku = value(row, mapping, "sku");
        String title = value(row, mapping, "title");

        for (String required : ImportFieldCatalog.REQUIRED_FIELDS) {
            if (isBlank(value(row, mapping, required))) {
                errors.add(new FieldError(required, required + " is required"));
            }
        }

        Integer packQuantity = null;
        String packRaw = value(row, mapping, "packQuantity");
        if (!isBlank(packRaw)) {
            try {
                packQuantity = Integer.parseInt(packRaw.trim());
                if (packQuantity < 0) {
                    errors.add(new FieldError("packQuantity", "Pack quantity must be zero or positive"));
                }
            } catch (NumberFormatException e) {
                errors.add(new FieldError("packQuantity", "Pack quantity is not a valid number: " + packRaw));
            }
        }

        BigDecimal weight = parseDecimal(row, mapping, "weight", errors);
        BigDecimal height = parseDecimal(row, mapping, "height", errors);
        BigDecimal width = parseDecimal(row, mapping, "width", errors);
        BigDecimal length = parseDecimal(row, mapping, "length", errors);

        ProductStatus status = null;
        String statusRaw = value(row, mapping, "status");
        if (!isBlank(statusRaw)) {
            try {
                status = ProductStatus.valueOf(statusRaw.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                errors.add(new FieldError("status", "Unknown status: " + statusRaw));
            }
        }

        // Left null when the column is absent so the entity default (NEW)
        // applies; a blank cell must not be read as a condition of its own.
        com.priceintel.backend.constants.ProductCondition condition = null;
        String conditionRaw = value(row, mapping, "condition");
        if (!isBlank(conditionRaw)) {
            try {
                condition = com.priceintel.backend.constants.ProductCondition
                        .valueOf(conditionRaw.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                errors.add(new FieldError("condition",
                        "Unknown condition: " + conditionRaw + " (expected NEW, USED or REFURBISHED)"));
            }
        }

        if (!errors.isEmpty()) {
            return new Result(null, errors);
        }

        CreateProductRequest request = CreateProductRequest.builder()
                .sku(sku.trim())
                .title(title.trim())
                .brand(trimToNull(value(row, mapping, "brand")))
                .description(trimToNull(value(row, mapping, "description")))
                .category(trimToNull(value(row, mapping, "category")))
                .status(status)
                .condition(condition)
                .packQuantity(packQuantity)
                .weight(weight).height(height).width(width).length(length)
                .identifiers(buildIdentifiers(row, mapping))
                .images(buildImages(row, mapping))
                .attributes(buildAttributes(row, mapping))
                .build();

        return new Result(request, errors);
    }

    private static List<ProductIdentifierRequest> buildIdentifiers(Map<String, String> row, Map<String, String> mapping) {
        List<ProductIdentifierRequest> list = new ArrayList<>();
        addIdentifier(list, IdentifierType.ASIN, value(row, mapping, "asin"));
        addIdentifier(list, IdentifierType.UPC, value(row, mapping, "upc"));
        addIdentifier(list, IdentifierType.EAN, value(row, mapping, "ean"));
        addIdentifier(list, IdentifierType.GTIN, value(row, mapping, "gtin"));
        addIdentifier(list, IdentifierType.MPN, value(row, mapping, "mpn"));
        return list.isEmpty() ? null : list;
    }

    private static void addIdentifier(List<ProductIdentifierRequest> list, IdentifierType type, String value) {
        if (!isBlank(value)) {
            list.add(ProductIdentifierRequest.builder().type(type).originalValue(value.trim()).build());
        }
    }

    private static List<ProductImageRequest> buildImages(Map<String, String> row, Map<String, String> mapping) {
        String url = value(row, mapping, "imageUrl");
        if (isBlank(url)) {
            return null;
        }
        return List.of(ProductImageRequest.builder().url(url.trim()).build());
    }

    private static List<ProductAttributeRequest> buildAttributes(Map<String, String> row, Map<String, String> mapping) {
        String color = trimToNull(value(row, mapping, "color"));
        String size = trimToNull(value(row, mapping, "size"));
        String material = trimToNull(value(row, mapping, "material"));
        String model = trimToNull(value(row, mapping, "model"));
        String variant = trimToNull(value(row, mapping, "variant"));
        String country = trimToNull(value(row, mapping, "country"));

        if (color == null && size == null && material == null && model == null && variant == null && country == null) {
            return null;
        }
        return List.of(ProductAttributeRequest.builder()
                .color(color).size(size).material(material)
                .model(model).variant(variant).country(country).build());
    }

    private static BigDecimal parseDecimal(Map<String, String> row, Map<String, String> mapping,
                                           String field, List<FieldError> errors) {
        String raw = value(row, mapping, field);
        if (isBlank(raw)) {
            return null;
        }
        try {
            BigDecimal val = new BigDecimal(raw.trim());
            if (val.signum() < 0) {
                errors.add(new FieldError(field, field + " must be zero or positive"));
            }
            return val;
        } catch (NumberFormatException e) {
            errors.add(new FieldError(field, field + " is not a valid number: " + raw));
            return null;
        }
    }

    private static String value(Map<String, String> row, Map<String, String> mapping, String fieldKey) {
        String header = mapping.get(fieldKey);
        if (header == null) {
            return null;
        }
        return row.get(header);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String trimToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }
}
