-- A tracked marketplace listing, and a record of every time its price moved.
--
-- Until now a marketplace search was ephemeral: the Search Product page fetched
-- listings, judged them, showed them and threw them away. Search the same term
-- tomorrow and yesterday's prices are gone — and no marketplace will sell them
-- back to you, because Amazon publishes today's price and nothing else. Price
-- history is the one asset here that cannot be bought, only accumulated, and it
-- was being discarded on every search.
--
-- Keyed by (marketplace, item) rather than by tenant, matching
-- listing_price_snapshots: two clients tracking the same ASIN thicken one
-- series instead of keeping two thin ones. What a listing costs is a fact about
-- the market, not about the client who happened to look.

CREATE TABLE IF NOT EXISTS marketplace_items (
    id                  BIGSERIAL PRIMARY KEY,
    marketplace         VARCHAR(20)  NOT NULL,
    marketplace_item_id VARCHAR(100) NOT NULL,

    title               VARCHAR(500),
    brand               VARCHAR(150),
    url                 VARCHAR(1000),
    seller              VARCHAR(200),
    condition           VARCHAR(50),

    -- The most recent observation. History lives in listing_price_snapshots;
    -- these are here so a list can be rendered without joining to it.
    currency            VARCHAR(10),
    item_price          NUMERIC(12,2),
    shipping            NUMERIC(12,2),
    landed_price        NUMERIC(12,2),
    availability        VARCHAR(50),

    -- The codes the marketplace publishes, as the JSON the API returns. Stored
    -- whole rather than split into columns because which codes exist varies by
    -- brand: a British seller registers an EAN and no UPC.
    identifiers         TEXT,

    -- first_seen answers "how long have we watched this", last_seen answers "is
    -- it still listed", last_changed answers "is this price stale or merely
    -- steady". The third cannot be derived from the other two, and without it a
    -- price unchanged for a month is indistinguishable from one nobody checked.
    first_seen_at       TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    last_seen_at        TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    last_changed_at     TIMESTAMP(6) WITH TIME ZONE,

    observation_count   INTEGER NOT NULL DEFAULT 0,
    change_count        INTEGER NOT NULL DEFAULT 0,

    created_at          TIMESTAMP(6) WITHOUT TIME ZONE,
    created_by          VARCHAR(255),
    updated_at          TIMESTAMP(6) WITHOUT TIME ZONE,
    updated_by          VARCHAR(255),

    CONSTRAINT uk_marketplace_item UNIQUE (marketplace, marketplace_item_id)
);

CREATE INDEX IF NOT EXISTS idx_marketplace_item_seen
    ON marketplace_items (last_seen_at DESC);
CREATE INDEX IF NOT EXISTS idx_marketplace_item_changed
    ON marketplace_items (last_changed_at DESC);

-- One row per field that actually moved.
--
-- Snapshots record what a value WAS at a moment; this records that it CHANGED,
-- which is a different question and the one a person asks. Both are kept: the
-- snapshot series draws the chart, the events answer "what happened and when"
-- without every reader re-deriving it by comparing adjacent rows — and getting
-- it wrong wherever an unchanged duplicate sits between two real moves.
CREATE TABLE IF NOT EXISTS price_change_events (
    id                  BIGSERIAL PRIMARY KEY,
    marketplace         VARCHAR(20)  NOT NULL,
    marketplace_item_id VARCHAR(100) NOT NULL,

    -- PRICE, SHIPPING, LANDED_PRICE, AVAILABILITY.
    field               VARCHAR(30)  NOT NULL,

    -- Text, so one table holds both "99.00 -> 94.05" and
    -- "IN_STOCK -> OUT_OF_STOCK". The numeric columns below carry the
    -- arithmetic for the money fields, and stay null for the rest.
    old_value           VARCHAR(200),
    new_value           VARCHAR(200),

    change_amount       NUMERIC(12,2),
    change_pct          NUMERIC(9,4),
    currency            VARCHAR(10),

    observed_at         TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    previous_observed_at TIMESTAMP(6) WITH TIME ZONE,

    created_at          TIMESTAMP(6) WITHOUT TIME ZONE,
    created_by          VARCHAR(255),
    updated_at          TIMESTAMP(6) WITHOUT TIME ZONE,
    updated_by          VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_price_change_item
    ON price_change_events (marketplace, marketplace_item_id, observed_at DESC);
CREATE INDEX IF NOT EXISTS idx_price_change_time
    ON price_change_events (observed_at DESC);

-- A price seen on a marketplace search belongs to no product, so it has no
-- competitor_listings row to point at. The series is read by (marketplace,
-- item) anyway — that index has existed since the beginning — so the row id was
-- never what joined the history together. Making it optional lets an item's
-- history start the first time it is seen and carry on unbroken if it is later
-- attached to a product, instead of beginning only at the moment of attachment.
ALTER TABLE listing_price_snapshots ALTER COLUMN listing_id DROP NOT NULL;
