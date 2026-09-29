package com.priceintel.backend.utils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.priceintel.backend.constants.ImportFieldCatalog;

/**
 * Works out which spreadsheet column feeds which product field.
 * Produces a "suggested" mapping from headers, and merges any user overrides.
 */
public final class ColumnMappingResolver {

    private ColumnMappingResolver() {
    }

    /**
     * Auto-suggests a mapping of {fieldKey -> actualHeader} by matching each
     * header against the field aliases in {@link ImportFieldCatalog}.
     */
    public static Map<String, String> suggest(List<String> headers) {
        Map<String, String> mapping = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : ImportFieldCatalog.FIELD_ALIASES.entrySet()) {
            String fieldKey = entry.getKey();
            Set<String> aliases = entry.getValue();
            for (String header : headers) {
                String normalized = ImportFieldCatalog.normalizeKey(header);
                if (aliases.contains(normalized)) {
                    mapping.put(fieldKey, header);
                    break;
                }
            }
        }
        return mapping;
    }

    /**
     * Effective mapping = auto-suggested, with any user-supplied overrides
     * ({fieldKey -> header}) applied on top.
     */
    public static Map<String, String> resolve(List<String> headers, Map<String, String> overrides) {
        Map<String, String> mapping = suggest(headers);
        if (overrides != null) {
            overrides.forEach((fieldKey, header) -> {
                if (header != null && !header.isBlank()) {
                    mapping.put(fieldKey, header);
                }
            });
        }
        return mapping;
    }
}
