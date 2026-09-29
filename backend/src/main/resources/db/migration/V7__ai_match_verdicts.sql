-- Cached AI judgements on "is this candidate the same product".
--
-- Without this, every visit to Match Review would re-ask the model about
-- listings it has already judged. At 8000 tokens/minute and ~300 tokens per
-- judgement, one pass over 245 candidates is roughly ten minutes of quota — so
-- re-asking is not merely wasteful, it makes the screen unusable.
--
-- Keyed by the pair being compared, not by our row id, so two clients tracking
-- the same ASIN share one verdict: the question "is this ASIN the same product
-- as that one" has the same answer whoever asks it.

CREATE TABLE IF NOT EXISTS ai_match_verdicts (
    id                BIGSERIAL PRIMARY KEY,
    product_id        BIGINT       NOT NULL,
    listing_id        BIGINT       NOT NULL,
    marketplace       VARCHAR(20),
    marketplace_item_id VARCHAR(100),

    decision          VARCHAR(20)  NOT NULL,
    score             INTEGER,
    reason            VARCHAR(1000),

    -- Which engine produced this, so a verdict can be re-run selectively after
    -- a model or prompt change instead of wholesale.
    provider          VARCHAR(20)  NOT NULL,
    model             VARCHAR(60),
    prompt_version    VARCHAR(30),
    total_tokens      INTEGER,

    created_at        TIMESTAMP,
    created_by        VARCHAR(255),
    updated_at        TIMESTAMP,
    updated_by        VARCHAR(255),

    CONSTRAINT fk_verdict_listing FOREIGN KEY (listing_id)
        REFERENCES competitor_listings (id) ON DELETE CASCADE
);

-- One current verdict per pair per prompt version. A new prompt version writes
-- a new row rather than overwriting, so the two can be compared before the old
-- one is trusted less.
CREATE UNIQUE INDEX IF NOT EXISTS uq_verdict_pair
    ON ai_match_verdicts (product_id, listing_id, prompt_version);

CREATE INDEX IF NOT EXISTS idx_verdict_product ON ai_match_verdicts (product_id);
