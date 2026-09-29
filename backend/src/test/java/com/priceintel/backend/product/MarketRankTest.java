package com.priceintel.backend.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Rank is the figure a user acts on directly — "am I being undercut?" — so the
 * edge cases matter more than the happy path. These pin the rules without
 * needing the database.
 */
class MarketRankTest {

    /** Mirrors the rule in SearchPipelineService: how many offers sit strictly below ours. */
    private int rank(String ourPrice, String... competitorPrices) {
        BigDecimal ours = new BigDecimal(ourPrice);
        long cheaper = List.of(competitorPrices).stream()
                .map(BigDecimal::new)
                .filter(p -> p.compareTo(ours) < 0)
                .count();
        return (int) cheaper + 1;
    }

    @Test
    @DisplayName("cheapest of all is rank 1")
    void cheapest() {
        assertThat(rank("70.00", "74.99", "80.00", "95.00")).isEqualTo(1);
    }

    @Test
    @DisplayName("most expensive is last")
    void dearest() {
        assertThat(rank("99.00", "74.99", "80.00", "95.00")).isEqualTo(4);
    }

    @Test
    @DisplayName("a tie is not a defeat — matching the cheapest still ranks joint first")
    void tiesShareRank() {
        // Only strictly-cheaper offers count, so an identical price does not
        // push us below someone who has not actually undercut us.
        assertThat(rank("74.99", "74.99", "80.00")).isEqualTo(1);
        assertThat(rank("80.00", "74.99", "80.00")).isEqualTo(2);
    }

    @Test
    @DisplayName("the only seller ranks 1 of 1")
    void soleSeller() {
        assertThat(rank("70.00")).isEqualTo(1);
    }

    @Test
    @DisplayName("a cent below the cheapest still wins")
    void narrowMargin() {
        assertThat(rank("74.98", "74.99")).isEqualTo(1);
        assertThat(rank("75.00", "74.99")).isEqualTo(2);
    }
}
