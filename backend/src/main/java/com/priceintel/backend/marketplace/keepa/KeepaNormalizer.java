package com.priceintel.backend.marketplace.keepa;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceintel.backend.constants.IdentifierType;
import com.priceintel.backend.dto.request.CreateProductRequest;
import com.priceintel.backend.dto.request.ProductAttributeRequest;
import com.priceintel.backend.dto.request.ProductIdentifierRequest;
import com.priceintel.backend.exception.MarketplaceApiException;
import com.priceintel.backend.marketplace.Marketplace;
import com.priceintel.backend.marketplace.model.PriceSnapshot;

import lombok.RequiredArgsConstructor;

/**
 * Converts Keepa product JSON into our canonical models. Keepa encodes prices as
 * integer cents (-1 = no data) and timestamps as "Keepa minutes". Parsing is
 * defensive: missing fields degrade to null rather than throwing.
 */
@Component
@RequiredArgsConstructor
public class KeepaNormalizer {

    private final ObjectMapper objectMapper;
    private final KeepaProperties props;

    // Keepa csv / stats.current index positions.
    private static final int IDX_AMAZON = 0;
    private static final int IDX_NEW = 1;
    private static final int IDX_BUY_BOX = 18;
    // Keepa time base: minutes since epoch offset.
    private static final long KEEPA_EPOCH_OFFSET_MIN = 21564000L;

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json == null ? "{}" : json);
        } catch (Exception e) {
            throw new MarketplaceApiException("Could not parse Keepa response JSON: " + e.getMessage(), e);
        }
    }

    /** The first product node from a Keepa response ({@code products[0]}). */
    public JsonNode firstProduct(String productJson) {
        JsonNode products = parse(productJson).path("products");
        return products.isArray() && !products.isEmpty() ? products.get(0)
                : objectMapper.createObjectNode();
    }

    /** Builds the canonical product from a Keepa product payload. */
    public CreateProductRequest toCreateProductRequest(String productJson, String asin) {
        JsonNode p = firstProduct(productJson);

        String title = text(p, "title", "Amazon product " + asin);
        String brand = text(p, "brand", text(p, "manufacturer", null));

        List<ProductIdentifierRequest> identifiers = new ArrayList<>();
        identifiers.add(ProductIdentifierRequest.builder()
                .type(IdentifierType.ASIN).originalValue(asin).build());
        for (JsonNode ean : p.path("eanList")) {
            addId(identifiers, IdentifierType.EAN, ean.asText(""));
        }
        for (JsonNode upc : p.path("upcList")) {
            addId(identifiers, IdentifierType.UPC, upc.asText(""));
        }

        List<ProductAttributeRequest> attributes = new ArrayList<>();
        String model = text(p, "model", text(p, "partNumber", null));
        String color = text(p, "color", null);
        String size = text(p, "size", null);
        if (model != null || color != null || size != null) {
            attributes.add(ProductAttributeRequest.builder().model(model).color(color).size(size).build());
        }

        return CreateProductRequest.builder()
                .sku(asin) // ASIN as the canonical SKU for Amazon-sourced products
                .title(title)
                .brand(brand)
                .category(text(p, "productGroup", null))
                .identifiers(identifiers)
                .attributes(attributes.isEmpty() ? null : attributes)
                .build();
    }

    /**
     * The current price (in major units): prefers Buy Box, then Amazon, then New.
     * Returns null when Keepa has no current price.
     */
    public BigDecimal extractCurrentPrice(String productJson) {
        JsonNode current = firstProduct(productJson).path("stats").path("current");
        if (!current.isArray()) {
            return null;
        }
        for (int idx : new int[]{IDX_BUY_BOX, IDX_AMAZON, IDX_NEW}) {
            BigDecimal price = cents(current, idx);
            if (price != null) {
                return price;
            }
        }
        return null;
    }

    public PriceSnapshot toPriceSnapshot(String productJson, String asin) {
        BigDecimal price = extractCurrentPrice(productJson);
        return PriceSnapshot.builder()
                .marketplace(Marketplace.AMAZON)
                .marketplaceItemId(asin)
                .itemPrice(price)
                // Keepa reports the item price only — shipping is unknown, not free.
                .shipping(null)
                .landedPrice(price)
                .currency(props.currency())
                .observedAt(LocalDateTime.now())
                .mocked(false)
                .build();
    }

    /** One point in a Keepa price history. */
    public record PricePoint(LocalDateTime observedAt, BigDecimal price) {
    }

    /**
     * Parses the Amazon (or Buy Box) price history from the product's csv array.
     * Each csv[i] is a flat [keepaMinutes, cents, keepaMinutes, cents, ...] list.
     */
    public List<PricePoint> priceHistory(String productJson) {
        JsonNode csv = firstProduct(productJson).path("csv");
        List<PricePoint> points = new ArrayList<>();
        JsonNode series = csv.path(IDX_BUY_BOX);
        if (!series.isArray() || series.isEmpty()) {
            series = csv.path(IDX_AMAZON);
        }
        if (!series.isArray()) {
            return points;
        }
        for (int i = 0; i + 1 < series.size(); i += 2) {
            long keepaMinutes = series.get(i).asLong(-1);
            long value = series.get(i + 1).asLong(-1);
            if (keepaMinutes < 0 || value < 0) {
                continue;
            }
            long epochMs = (keepaMinutes + KEEPA_EPOCH_OFFSET_MIN) * 60_000L;
            LocalDateTime at = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault());
            points.add(new PricePoint(at, BigDecimal.valueOf(value).divide(BigDecimal.valueOf(100))));
        }
        return points;
    }

    // ---------- helpers ----------

    private BigDecimal cents(JsonNode arr, int idx) {
        if (idx >= arr.size()) {
            return null;
        }
        long v = arr.get(idx).asLong(-1);
        if (v < 0) {
            return null; // -1 = no data
        }
        return BigDecimal.valueOf(v).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private void addId(List<ProductIdentifierRequest> list, IdentifierType type, String value) {
        if (value != null && !value.isBlank()) {
            list.add(ProductIdentifierRequest.builder().type(type).originalValue(value).build());
        }
    }

    private String text(JsonNode node, String field, String defaultValue) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() || v.asText().isBlank() ? defaultValue : v.asText();
    }
}
