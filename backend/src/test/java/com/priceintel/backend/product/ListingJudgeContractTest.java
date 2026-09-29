package com.priceintel.backend.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceintel.backend.ai.AiCompletion;
import com.priceintel.backend.ai.AiCompletionRequest;
import com.priceintel.backend.ai.AiGateway;
import com.priceintel.backend.dto.response.MatchedListing;
import com.priceintel.backend.exception.AiException;
import com.priceintel.backend.service.impl.ListingJudgeService;

/**
 * What the judge is allowed to claim.
 *
 * <p>These exist because a failed judge once reported every listing it had never
 * looked at as a match — Beats Studio Buds returned as a confirmed competitor to
 * AirPods. {@code matches} means "the model ruled on this and said yes", and
 * when the model did not run there is nothing that can honestly go in it.</p>
 */
class ListingJudgeContractTest {

    /** A gateway in a chosen state — configured, broken, or absent. */
    private record FakeGateway(boolean available, String reply) implements AiGateway {

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public AiCompletion complete(AiCompletionRequest request) {
            if (reply == null) {
                throw new AiException("provider exploded");
            }
            return AiCompletion.builder().content(reply).model("test-model").build();
        }
    }

    private ListingJudgeService judgeWith(AiGateway gateway) {
        return new ListingJudgeService(gateway, new ObjectMapper(),
                new com.priceintel.backend.ai.OpenAiProperties());
    }

    private List<MatchedListing> twoListings() {
        return new java.util.ArrayList<>(List.of(
                MatchedListing.builder().marketplace("AMAZON").marketplaceItemId("B01")
                        .title("Apple AirPods 4 Wireless Earbuds").build(),
                MatchedListing.builder().marketplace("AMAZON").marketplaceItemId("B02")
                        .title("Beats Studio Buds").build()));
    }

    @Test
    @DisplayName("no provider configured: nothing is claimed as a match")
    void notConfiguredClaimsNothing() {
        var v = judgeWith(new FakeGateway(false, null))
                .judgeListings("title: Apple AirPods 4", twoListings());

        assertThat(v.judgedByAi()).isFalse();
        assertThat(v.status()).isEqualTo("NOT_JUDGED");
        assertThat(v.unavailableReason()).isEqualTo(ListingJudgeService.NOT_CONFIGURED);
        // The whole point: neither list may be populated.
        assertThat(v.matches()).isNull();
        assertThat(v.rejected()).isNull();
        // Retrying cannot fix a missing key.
        assertThat(v.retryable()).isFalse();
        assertThat(v.searchedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("provider failed: still nothing claimed, but worth retrying")
    void providerErrorIsRetryable() {
        var v = judgeWith(new FakeGateway(true, null))
                .judgeListings("title: Apple AirPods 4", twoListings());

        assertThat(v.status()).isEqualTo("NOT_JUDGED");
        assertThat(v.matches()).isNull();
        assertThat(v.rejected()).isNull();
        assertThat(v.retryable()).isTrue();
        assertThat(v.unavailableReason()).startsWith("PROVIDER_ERROR");
    }

    @Test
    @DisplayName("an unjudged listing carries no score and no missing-evidence row")
    void unjudgedListingsSayNothing() {
        List<MatchedListing> listings = twoListings();
        judgeWith(new FakeGateway(false, null)).judgeListings("title: Apple AirPods 4", listings);

        assertThat(listings).allSatisfy(l -> {
            assertThat(l.getDecision()).isEqualTo("UNCERTAIN");
            assertThat(l.getJudgedBy()).isEqualTo("RULES");
            // Not 50 — no similarity is computed, so a number would be invented.
            assertThat(l.getScore()).isNull();
            // Not ["AI verdict"] — that is a response-level fact, not a per-row gap.
            assertThat(l.getMissingEvidence()).isEmpty();
        });
    }

    @Test
    @DisplayName("a real verdict populates both lists and nothing else")
    void aiVerdictsSplitTheLists() {
        String reply = """
                {"results":[
                  {"id":1,"decision":"MATCH","score":95,"confidence":0.9,
                   "matchedAttributes":["brand","model"],"conflicts":[],
                   "missingEvidence":[],"reason":"same product"},
                  {"id":2,"decision":"NOT_MATCH","score":5,"confidence":0.95,
                   "matchedAttributes":[],"conflicts":["brand"],
                   "missingEvidence":[],"reason":"different brand"}]}
                """;
        var v = judgeWith(new FakeGateway(true, reply))
                .judgeListings("title: Apple AirPods 4", twoListings());

        assertThat(v.judgedByAi()).isTrue();
        assertThat(v.status()).isEqualTo("FOUND");
        assertThat(v.retryable()).isFalse();
        assertThat(v.matches()).extracting(MatchedListing::getMarketplaceItemId)
                .containsExactly("B01");
        assertThat(v.rejected()).extracting(MatchedListing::getMarketplaceItemId)
                .containsExactly("B02");
    }

    @Test
    @DisplayName("a listing the model skipped is in neither list")
    void skippedListingsAreNotMatches() {
        // The model answers for id 1 only; id 2 falls back to rules.
        String reply = """
                {"results":[{"id":1,"decision":"MATCH","score":95,"reason":"same product"}]}
                """;
        var v = judgeWith(new FakeGateway(true, reply))
                .judgeListings("title: Apple AirPods 4", twoListings());

        assertThat(v.judgedByAi()).isTrue();
        assertThat(v.matches()).extracting(MatchedListing::getMarketplaceItemId)
                .containsExactly("B01");
        assertThat(v.rejected()).isEmpty();
        assertThat(v.searchedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("nothing to judge is NO_LISTINGS, not NOT_JUDGED")
    void emptySearchKeepsItsOwnStatus() {
        var v = judgeWith(new FakeGateway(true, "{}")).judgeListings("title: x", List.of());

        assertThat(v.status()).isEqualTo("NO_LISTINGS");
        assertThat(v.matches()).isNull();
        assertThat(v.rejected()).isNull();
    }
}
