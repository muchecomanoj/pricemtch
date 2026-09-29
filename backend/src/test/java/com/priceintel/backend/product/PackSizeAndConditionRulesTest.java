package com.priceintel.backend.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.priceintel.backend.constants.IdentifierType;
import com.priceintel.backend.constants.ProductCondition;
import com.priceintel.backend.entity.CompetitorListing;
import com.priceintel.backend.entity.Product;
import com.priceintel.backend.entity.ProductIdentifier;
import com.priceintel.backend.service.impl.MatchScoringService;

/**
 * FR-MATCH-001: pack size and condition are hard rules.
 *
 * <p>"Exact ASIN/GTIN match is a strong positive signal but does not bypass
 * pack-size and condition checks." A 2-pack is a different offer from a single
 * unit, so its price is not a comparable — however confident the match looks.</p>
 */
class PackSizeAndConditionRulesTest {

    private final MatchScoringService scorer = new MatchScoringService();

    private static final String ASIN = "B0DBHWQ58D";

    // ---------- the case the document itself describes ----------

    @Test
    void aTwoPackIsBlockedAgainstOurSingleUnit() {
        MatchScoringService.MatchAssessment a =
                scorer.assess(singleUnit(), listing("Widget Pro 2 Pack", "NEW"));

        assertThat(a.isBlocked()).isTrue();
        assertThat(a.getBlockedReason())
                .contains("Pack size differs")
                .contains("single unit")
                .contains("2-pack");
    }

    @Test
    void anExactIdentifierMatchDoesNotOverrideThePackRule() {
        // The listing carries our exact ASIN and scores highly on every other
        // signal — and is still not a comparable.
        CompetitorListing twoPack = listing("Widget Pro " + ASIN + " 2 Pack", "NEW");
        twoPack.setMarketplaceItemId(ASIN);

        MatchScoringService.MatchAssessment a = scorer.assess(singleUnit(), twoPack);

        assertThat(a.getScore()).isGreaterThan(40);   // looks like a strong match
        assertThat(a.isBlocked()).isTrue();           // and is blocked anyway
    }

    @Test
    void matchingPackSizesAreNotBlocked() {
        Product sixPack = singleUnit();
        sixPack.setPackQuantity(6);

        assertThat(scorer.assess(sixPack, listing("Widget Pro 6 Pack", "NEW")).isBlocked())
                .isFalse();
    }

    @Test
    void aListingThatSaysNothingAboutQuantityIsNotBlocked() {
        // Most listings never mention packaging. Treating silence as "one" would
        // block honest matches on the majority of the catalogue.
        assertThat(scorer.assess(singleUnit(), listing("Widget Pro Wireless", "NEW")).isBlocked())
                .isFalse();
    }

    // ---------- reading the pack size out of a title ----------

    @ParameterizedTest
    @CsvSource({
            "'Widget 2 Pack', 2",
            "'Widget 2-Pack', 2",
            "'Widget Pack of 3', 3",
            "'Widget 4 count', 4",
            "'Widget 6pk', 6",
            "'Widget Twin Pack', 2",
            "'Widget 12 pcs', 12"
    })
    void packSizeIsReadFromCommonPhrasings(String title, int expected) {
        assertThat(scorer.packSizeOf(title)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Widget Pro Wireless",
            "Widget Model 2000",          // a model number is not a pack size
            "Widget 256GB Storage",       // nor is a capacity
            "Widget 5 Star Rated"
    })
    void unrelatedNumbersAreNotMistakenForPackSizes(String title) {
        assertThat(scorer.packSizeOf(title)).isNull();
    }

    // ---------- condition ----------

    @Test
    void aUsedListingIsBlockedAgainstOurNewProduct() {
        MatchScoringService.MatchAssessment a =
                scorer.assess(singleUnit(), listing("Widget Pro", "USED"));

        assertThat(a.isBlocked()).isTrue();
        assertThat(a.getBlockedReason()).contains("Condition differs");
    }

    @Test
    void refurbishedIsBlockedAgainstNew() {
        assertThat(scorer.assess(singleUnit(), listing("Widget Pro", "REFURBISHED")).isBlocked())
                .isTrue();
    }

    @Test
    void aUsedListingIsComparableWhenOurProductIsAlsoUsed() {
        Product used = singleUnit();
        used.setCondition(ProductCondition.USED);

        assertThat(scorer.assess(used, listing("Widget Pro", "USED")).isBlocked()).isFalse();
    }

    @Test
    void anUnreadableConditionDoesNotBlock() {
        // Blocking on a string we cannot interpret would hide real comparables.
        assertThat(scorer.assess(singleUnit(), listing("Widget Pro", "SOMETHING_ODD")).isBlocked())
                .isFalse();
    }

    @Test
    void packConflictIsReportedAheadOfConditionWhenBothDiffer() {
        MatchScoringService.MatchAssessment a =
                scorer.assess(singleUnit(), listing("Widget 2 Pack", "USED"));

        assertThat(a.getBlockedReason()).startsWith("Pack size differs");
    }

    // ---------- helpers ----------

    private Product singleUnit() {
        Product p = Product.builder()
                .sku("WID-1").title("Widget Pro").brand("Widget")
                .packQuantity(1).condition(ProductCondition.NEW)
                .build();
        p.setIdentifiers(new java.util.ArrayList<>(List.of(
                ProductIdentifier.builder()
                        .type(IdentifierType.ASIN).originalValue(ASIN).normalizedValue(ASIN).build())));
        return p;
    }

    private CompetitorListing listing(String title, String condition) {
        return CompetitorListing.builder()
                .marketplace("AMAZON").marketplaceItemId("X1")
                .title(title).condition(condition).build();
    }
}
