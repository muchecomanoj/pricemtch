package com.priceintel.backend.constants;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Defines the canonical product fields an import can populate, along with the
 * header aliases that auto-map to each one. Used for column mapping.
 */
public final class ImportFieldCatalog {

    private ImportFieldCatalog() {
    }

    /** Canonical field key -> set of accepted header aliases (all normalized). */
    public static final Map<String, Set<String>> FIELD_ALIASES = new LinkedHashMap<>();

    /** Fields that must be present (mapped and non-empty) for a row to import. */
    public static final List<String> REQUIRED_FIELDS = List.of("sku", "title");

    static {
        FIELD_ALIASES.put("sku", Set.of("sku", "skucode", "productsku", "itemsku"));
        FIELD_ALIASES.put("title", Set.of("title", "name", "productname", "producttitle", "itemname"));
        FIELD_ALIASES.put("brand", Set.of("brand", "manufacturer", "make"));
        FIELD_ALIASES.put("description", Set.of("description", "desc", "details"));
        FIELD_ALIASES.put("category", Set.of("category", "categoryname", "producttype"));
        FIELD_ALIASES.put("status", Set.of("status", "productstatus"));
        FIELD_ALIASES.put("condition", Set.of("condition", "productcondition", "itemcondition"));
        FIELD_ALIASES.put("packQuantity", Set.of("packquantity", "packqty", "quantity", "packsize"));
        FIELD_ALIASES.put("weight", Set.of("weight", "wt"));
        FIELD_ALIASES.put("height", Set.of("height"));
        FIELD_ALIASES.put("width", Set.of("width"));
        FIELD_ALIASES.put("length", Set.of("length"));
        FIELD_ALIASES.put("asin", Set.of("asin"));
        FIELD_ALIASES.put("upc", Set.of("upc"));
        FIELD_ALIASES.put("ean", Set.of("ean"));
        FIELD_ALIASES.put("gtin", Set.of("gtin"));
        FIELD_ALIASES.put("mpn", Set.of("mpn", "manufacturerpartnumber", "partnumber"));
        FIELD_ALIASES.put("color", Set.of("color", "colour"));
        FIELD_ALIASES.put("size", Set.of("size"));
        FIELD_ALIASES.put("material", Set.of("material"));
        FIELD_ALIASES.put("model", Set.of("model", "modelnumber"));
        FIELD_ALIASES.put("variant", Set.of("variant", "variation"));
        FIELD_ALIASES.put("country", Set.of("country", "countryoforigin"));
        FIELD_ALIASES.put("imageUrl", Set.of("imageurl", "image", "imagelink", "picture"));
    }

    /** Normalizes a header/field for comparison (lower-case, alphanumeric only). */
    public static String normalizeKey(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
