package com.priceintel.backend.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.priceintel.backend.ai.AiCompletion;
import com.priceintel.backend.ai.AiCompletionRequest;
import com.priceintel.backend.ai.AiGateway;
import com.priceintel.backend.ai.OpenAiProperties;
import com.priceintel.backend.dto.response.MatchedListing;
import com.priceintel.backend.exception.AiException;
import com.priceintel.backend.service.impl.ListingJudgeService;

/**
 * Judging more listings than fit in one request.
 *
 * <p>Measured on the live model: 40 verdicts cost 2,897 output tokens in 28
 * seconds, and 100 in a single request works but takes 82 and loses everything
 * if the reply truncates. So the work is split, and a batch that fails costs
 * only its own listings.</p>
 */
class ListingJudgeBatchingTest {

    /** Answers every id it was given, and counts the requests it received. */
    private static final class CountingGateway implements AiGateway {

        private final AtomicInteger calls = new AtomicInteger();
        // Batches run at the same time, so both the writes and the order are
        // concurrent; the assertions below never depend on which arrived first.
        private final List<Integer> sizes = java.util.Collections.synchronizedList(new ArrayList<>());
        private final int failFrom;

        CountingGateway(int failFrom) {
            this.failFrom = failFrom;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public AiCompletion complete(AiCompletionRequest request) {
            int call = calls.incrementAndGet();
            // One numbered line per candidate, so the count is the batch size.
            int size = (int) request.getUserPrompt().lines()
                    .filter(l -> l.matches("^\\d+\\. .*")).count();
            sizes.add(size);
            if (call >= failFrom) {
                throw new AiException("provider exploded");
            }
            StringBuilder json = new StringBuilder("{\"results\":[");
            for (int i = 1; i <= size; i++) {
                json.append(i > 1 ? "," : "")
                        .append("{\"id\":").append(i)
                        .append(",\"decision\":\"MATCH\",\"score\":88,\"confidence\":0.9,")
                        .append("\"reason\":\"same product\"}");
            }
            return AiCompletion.builder().content(json.append("]}").toString())
                    .model("test-model").build();
        }
    }

    private List<MatchedListing> listings(int count) {
        List<MatchedListing> out = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            out.add(MatchedListing.builder().marketplace("AMAZON")
                    .marketplaceItemId("B" + i).title("Apple AirPods 4 variant " + i).build());
        }
        return out;
    }

    private ListingJudgeService judge(AiGateway gateway, int batchSize, int max) {
        OpenAiProperties props = new OpenAiProperties();
        props.setJudgeBatchSize(batchSize);
        props.setJudgeMaxListings(max);
        props.setRequestsPerMinute(60);                         // OpenAI's quota
        return new ListingJudgeService(gateway, new ObjectMapper(), props);
    }

    @Test
    @DisplayName("90 listings are judged in batches, and every one gets a verdict")
    void allListingsAreJudged() {
        CountingGateway gateway = new CountingGateway(Integer.MAX_VALUE);

        var verdicts = judge(gateway, 40, 120).judgeListings("AirPods 4", listings(90));

        // Not 40 + 40 + 10. The batches run side by side, so the wait is the
        // longest one: three thirties answer sooner than a forty and a ten,
        // for the same three requests.
        assertThat(gateway.calls.get()).isEqualTo(3);
        assertThat(gateway.sizes).containsExactly(30, 30, 30);
        assertThat(verdicts.matches()).hasSize(90);
        assertThat(verdicts.judgedByAi()).isTrue();
    }

    @Test
    @DisplayName("a batch that fits is still one request")
    void oneBatchWhenItFits() {
        CountingGateway gateway = new CountingGateway(Integer.MAX_VALUE);

        judge(gateway, 40, 120).judgeListings("AirPods 4", listings(12));

        assertThat(gateway.calls.get()).isEqualTo(1);
        assertThat(gateway.sizes).containsExactly(12);
    }

    @Test
    @DisplayName("one failed batch does not throw away the verdicts of the others")
    void partialFailureKeepsWhatWasJudged() {
        // The old behaviour marked every listing unjudged when a request failed,
        // which with batching would discard good verdicts for one bad batch.
        CountingGateway gateway = new CountingGateway(3);

        var verdicts = judge(gateway, 40, 120).judgeListings("AirPods 4", listings(90));

        assertThat(verdicts.judgedByAi()).isTrue();
        assertThat(verdicts.unavailableReason()).isNull();
        assertThat(verdicts.matches()).hasSize(60);            // one batch of 30 failed
        assertThat(verdicts.status()).isEqualTo("FOUND");
    }

    @Test
    @DisplayName("when every batch fails, nothing is claimed as judged")
    void totalFailureClaimsNothing() {
        CountingGateway gateway = new CountingGateway(1);

        var verdicts = judge(gateway, 40, 120).judgeListings("AirPods 4", listings(50));

        assertThat(verdicts.judgedByAi()).isFalse();
        assertThat(verdicts.matches()).isNull();
        assertThat(verdicts.unavailableReason()).startsWith("PROVIDER_ERROR");
        assertThat(verdicts.retryable()).isTrue();
    }

    @Test
    @DisplayName("beyond the ceiling, the rest are left unjudged rather than guessed at")
    void ceilingIsRespected() {
        CountingGateway gateway = new CountingGateway(Integer.MAX_VALUE);

        var verdicts = judge(gateway, 40, 80).judgeListings("AirPods 4", listings(200));

        // 80, split evenly. In any order: the batches run side by side, so which
        // one reaches the gateway first varies from run to run.
        assertThat(gateway.sizes).containsExactlyInAnyOrder(27, 27, 26);
        assertThat(verdicts.matches()).hasSize(80);            // stops at the ceiling
        assertThat(verdicts.searchedCount()).isEqualTo(200);   // honest about the rest
    }

    @Test
    @DisplayName("the split fills the lanes, but never past what one reply can hold")
    void splitFillsTheLanes() {
        OpenAiProperties props = new OpenAiProperties();       // 40 max, 8 min, 3 lanes
        props.setRequestsPerMinute(60);                         // a call may start every second

        // Thirty listings — the size of an everyday two-marketplace search —
        // went as one batch and took thirty seconds. Three tens take a third
        // of that, because they are waited on together.
        assertThat(props.judgeSplitSize(30)).isEqualTo(10);

        // Too few to be worth splitting: three requests of four would each pay
        // the same fixed reading-and-reasoning cost as one request of twelve.
        assertThat(props.judgeSplitSize(12)).isEqualTo(12);
        assertThat(props.judgeSplitSize(5)).isEqualTo(5);

        // Beyond three lanes the batch size grows again rather than the lane
        // count, and stops at the size a reply can hold without truncating.
        assertThat(props.judgeSplitSize(120)).isEqualTo(40);
        assertThat(props.judgeSplitSize(200)).isEqualTo(40);
    }

    @Test
    @DisplayName("under a tight quota the listings are not split, because the batches would only queue")
    void tightQuotaDoesNotSplit() {
        OpenAiProperties props = new OpenAiProperties();
        props.setRequestsPerMinute(3);                          // Groq free tier: one call per 20s

        // Measured: 24 listings as three batches took 42s — three 2-second
        // answers separated by the limiter's 20-second gaps. One batch: ~5s.
        assertThat(props.judgeSplitSize(24)).isEqualTo(24);
        assertThat(props.judgeSplitSize(30)).isEqualTo(30);
        // Still never past what one reply can hold.
        assertThat(props.judgeSplitSize(90)).isEqualTo(30);     // 3 × 30, not 1 × 90
    }

    @Test
    @DisplayName("the output budget grows with the batch, within the model's limit")
    void tokenBudgetScalesWithBatch() {
        OpenAiProperties props = new OpenAiProperties();

        props.setJudgeBatchSize(15);
        assertThat(props.judgeMaxTokens()).isEqualTo(4_500);
        props.setJudgeBatchSize(40);
        assertThat(props.judgeMaxTokens()).isEqualTo(9_500);
        // Never past what the model will emit in one reply.
        props.setJudgeBatchSize(500);
        assertThat(props.judgeMaxTokens()).isEqualTo(16_000);
    }
}
