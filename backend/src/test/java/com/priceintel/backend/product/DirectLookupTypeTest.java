package com.priceintel.backend.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The null-handling that broke every title search.
 *
 * <p>Adding item-id lookups introduced {@code DIRECT_LOOKUP_TYPES.contains(type)}
 * where {@code type} is null for a title attempt. {@link Set#of} is documented to
 * throw {@link NullPointerException} from {@code contains(null)} rather than
 * answering false, so every keyword search failed in zero milliseconds with
 * "Cannot invoke Object.equals(Object)" — before any marketplace was called, and
 * reported as a provider error on channels that were never contacted.</p>
 */
class DirectLookupTypeTest {

    private static final Set<String> DIRECT_LOOKUP_TYPES = Set.of("ITEM_ID", "MARKETPLACE_ID");

    /** Mirrors ChannelSearchPlanner.isDirectLookup. */
    private boolean isDirectLookup(String identifierType) {
        return identifierType != null && DIRECT_LOOKUP_TYPES.contains(identifierType);
    }

    @Test
    @DisplayName("a title attempt has no type, and must not throw")
    void nullTypeIsSafe() {
        assertThatCode(() -> isDirectLookup(null)).doesNotThrowAnyException();
        assertThat(isDirectLookup(null)).isFalse();
    }

    @Test
    @DisplayName("Set.of genuinely throws on a null lookup — this is why the guard exists")
    void theUnguardedFormThrows() {
        assertThatCode(() -> DIRECT_LOOKUP_TYPES.contains(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("item ids are fetched directly; everything else is searched")
    void routesByType() {
        assertThat(isDirectLookup("ITEM_ID")).isTrue();
        assertThat(isDirectLookup("MARKETPLACE_ID")).isTrue();

        for (String searched : new String[] {"ASIN", "UPC", "EAN", "GTIN", "MPN"}) {
            assertThat(isDirectLookup(searched))
                    .as("%s is searched, not fetched", searched).isFalse();
        }
    }
}
