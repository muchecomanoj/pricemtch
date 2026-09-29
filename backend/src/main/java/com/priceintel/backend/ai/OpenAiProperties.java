package com.priceintel.backend.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * OpenAI configuration, bound from properties prefixed {@code openai}. The API
 * key comes from the environment.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "openai")
public class OpenAiProperties {

    private boolean enabled = false;
    private String apiKey;
    private String baseUrl = "https://api.openai.com/v1";
    private String model = "gpt-4o-mini";

    private int connectTimeoutMs = 5000;
    /**
     * How long to wait for a reply.
     *
     * <p>Must exceed the slowest batch the judge sends, and by a margin. At 30
     * seconds it did not: a batch of 40 listings takes about 28 seconds, so
     * every Amazon batch — whose titles are long — was cut off mid-reply and
     * reported as "no verdict", while a smaller eBay batch in the same search
     * finished and looked fine. The failure read as "Amazon listings are never
     * checked", which is not what was happening at all.</p>
     *
     * <p>A generous value costs nothing: the call ends when the model answers,
     * not when this expires.</p>
     */
    private int readTimeoutMs = 90000;
    private int maxRetries = 2;

    /**
     * Calls allowed per minute, shaped to the provider's quota.
     *
     * <p>Groq's free tier caps <em>tokens</em> per minute, not calls, so this is
     * a token budget expressed in calls and has to be re-derived whenever the
     * cost of a call changes. It did: the figure was set at fifteen when a match
     * was judged one pair at a time for ~490 tokens. Batched judging sends every
     * candidate in one request and the model reasons before answering, so a call
     * now costs ~2,400 tokens — measured from {@code x-ratelimit-remaining-tokens},
     * which fell 5941 → 3521 across two calls.</p>
     *
     * <p>8,000 ÷ 2,400 ≈ 3. Fifteen was budgeting 36,000 tokens a minute against
     * an 8,000 allowance.</p>
     *
     * <p>Overspending does not fail cleanly. Rather than a 429 the reply is
     * simply cut short, and a truncated reply in JSON mode comes back as HTTP 400
     * {@code json_validate_failed} with an empty {@code failed_generation} — a
     * bad-request code for what is really an exhausted quota. That is why the
     * judge failed intermittently and looked like a prompt problem.</p>
     */
    private int requestsPerMinute = 3;

    /**
     * How many listings are judged in one request.
     *
     * <p>Measured against the live model: 40 verdicts cost 2,897 output tokens
     * — about 72 each — and took 28 seconds, with nothing truncated. The old
     * limit of 15 was set before that was known and left most of the budget
     * unused.</p>
     *
     * <p>Larger batches are cheaper per listing but slower to come back, and a
     * truncated reply loses the whole batch, so this stays well inside the
     * model's output limit rather than at the edge of it.</p>
     */
    private int judgeBatchSize = 40;

    /**
     * The smallest batch worth sending on its own.
     *
     * <p>Every request carries a fixed cost — the model reads the instructions
     * and reasons before answering — so batches below this spend more time on
     * overhead than on verdicts.</p>
     */
    private int judgeMinBatchSize = 8;

    /**
     * How to split {@code count} listings so the answer comes back soonest.
     *
     * <p>Batches run at the same time, so the wait is the slowest batch, not
     * their sum. Sending 30 listings as one batch took 30 seconds; as three
     * batches of ten it is closer to twelve, for the same work and the same
     * cost.</p>
     *
     * <p>Bounded at both ends: never larger than {@link #judgeBatchSize}, where
     * a reply risks truncation, and never smaller than
     * {@link #judgeMinBatchSize}, where per-request overhead dominates.</p>
     */
    /** Above this spacing, a split batch waits longer in the queue than it saves. */
    private static final long MAX_SPLIT_SPACING_MS = 2_000;

    /** The gap the rate limiter enforces between two calls. */
    public long callSpacingMs() {
        return 60_000L / Math.max(1, requestsPerMinute);
    }

    public int judgeSplitSize(int count) {
        if (count <= 0) {
            return judgeBatchSize;
        }
        int maxSize = Math.max(1, judgeBatchSize);
        int minSize = Math.max(1, judgeMinBatchSize);
        // At least this many, or a batch would be too big to answer safely.
        int needed = (int) Math.ceil(count / (double) maxSize);
        // At most this many, or the batches are too small to be worth a request.
        int useful = Math.max(1, count / minSize);
        // Splitting only pays when the batches can actually start together. The
        // rate limiter spaces calls 60/RPM seconds apart, so under a tight quota
        // the "parallel" batches queue: on Groq's free tier (RPM 3) 24 listings
        // split three ways took 42 seconds — three 2-second answers with 20
        // seconds of waiting between each — where one batch takes about five.
        int lanesAllowed = callSpacingMs() <= MAX_SPLIT_SPACING_MS
                ? Math.max(1, judgeConcurrency) : 1;
        int lanes = Math.max(needed, Math.min(useful, lanesAllowed));
        // Divided evenly, so no lane is left finishing alone after the others.
        return (int) Math.ceil(count / (double) lanes);
    }

    /**
     * The most listings one search will judge, across all its batches.
     *
     * <p>A ceiling on time and spend, not a technical limit: 120 listings is
     * three requests and roughly a minute and a half. Beyond that a person is
     * waiting a long time for verdicts on listings they will never scroll to.</p>
     */
    private int judgeMaxListings = 120;

    /**
     * How many judging batches may be in flight at once.
     *
     * <p>Judging is waiting, not working: a batch of 40 takes about 28 seconds,
     * almost all of it spent waiting for the model. Run one after another, 80
     * listings kept a person waiting a minute for an answer the model could
     * produce in half that.</p>
     *
     * <p>Bounded rather than unlimited, because the provider's per-minute limit
     * still applies and a burst only queues behind it.</p>
     */
    private int judgeConcurrency = 3;

    /**
     * Output budget for one batch: enough for a verdict on every listing, plus
     * room for the model's reasoning, which counts against the same budget.
     *
     * <p>Measured at 72 tokens per verdict and 530-1000 for the reasoning. The
     * allowance here is deliberately generous — billing is on tokens used, not
     * on this cap, so headroom costs nothing and truncation costs the batch.</p>
     */
    public int judgeMaxTokens() {
        return Math.min(16_000, Math.max(4_000, judgeBatchSize * 200 + 1_500));
    }

    /**
     * A model that can search the web, for finding a product we only know by
     * name. Blank disables the feature rather than falling back to the ordinary
     * model, which would answer from memory and invent identifiers.
     */
    private String webSearchModel = "";

    /**
     * A model that can look at an image, for turning a photo into search words.
     *
     * <p>Separate from the ordinary model because vision is not general: on this
     * account {@code qwen/qwen3.6-27b} accepts images and the rest answer HTTP
     * 400. Blank disables image search rather than falling back to a text model
     * that cannot see.</p>
     */
    private String visionModel = "";

    public boolean isVisionAvailable() {
        return isConfigured() && visionModel != null && !visionModel.isBlank();
    }

    public boolean isWebSearchAvailable() {
        return isConfigured() && webSearchModel != null && !webSearchModel.isBlank();
    }

    /** Which provider the base URL points at — for the audit trail. */
    public com.priceintel.backend.constants.AiProvider provider() {
        return com.priceintel.backend.constants.AiProvider.fromBaseUrl(baseUrl);
    }

    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}
