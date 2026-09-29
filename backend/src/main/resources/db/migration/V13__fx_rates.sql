-- Currency handling for cross-marketplace comparison (Phase 2).
--
-- Prices already carried a currency code, but nothing ever used it. A Canadian
-- listing at C$95 and a US listing at $70 were sorted together and the median
-- of the two was labelled with whichever currency the first row happened to
-- have. The number looked authoritative and meant nothing — and it fed the
-- recommendation engine, so it set real prices.
--
-- Rates are STORED rather than fetched when a figure is displayed. A report run
-- twice on the same data must give the same answer; a live lookup would quietly
-- restate last month's margin every time the market moved. It also means the
-- platform keeps working when the rate source is unreachable, with a visibly
-- old rate instead of a blank screen.

CREATE TABLE IF NOT EXISTS fx_rates (
    id              BIGSERIAL PRIMARY KEY,

    -- One unit of base_currency costs `rate` units of quote_currency.
    base_currency   VARCHAR(3)   NOT NULL,
    quote_currency  VARCHAR(3)   NOT NULL,

    -- Six decimal places: a JPY/USD rate needs them, and rounding at the rate
    -- rather than at the converted amount loses cents on large figures.
    rate            NUMERIC(18,6) NOT NULL,

    -- When this rate was true, not when the row was written. The two differ
    -- whenever a rate is backfilled, and it is the former that a historical
    -- figure must be converted at.
    as_of           TIMESTAMP(6) WITH TIME ZONE NOT NULL,

    -- Where it came from — 'MANUAL', or a provider name. Shown beside converted
    -- figures, because "converted at a rate somebody typed in" and "converted at
    -- the ECB close" deserve different amounts of trust.
    source          VARCHAR(40)  NOT NULL DEFAULT 'MANUAL',

    created_at      TIMESTAMP(6) WITHOUT TIME ZONE,
    created_by      VARCHAR(255),
    updated_at      TIMESTAMP(6) WITHOUT TIME ZONE,
    updated_by      VARCHAR(255),

    CONSTRAINT chk_fx_rate_positive CHECK (rate > 0),
    CONSTRAINT chk_fx_rate_distinct CHECK (base_currency <> quote_currency)
);

-- One rate per pair per instant, so "the rate as of X" is never ambiguous.
CREATE UNIQUE INDEX IF NOT EXISTS uq_fx_rate_pair_asof
    ON fx_rates (base_currency, quote_currency, as_of);

-- Supports the only query that matters: newest rate for a pair at or before a
-- given moment.
CREATE INDEX IF NOT EXISTS idx_fx_rate_lookup
    ON fx_rates (base_currency, quote_currency, as_of DESC);

-- No fx_rate column on price_recommendations. A recommendation is derived from
-- a whole market that may span several currencies, so there is no single rate
-- that produced it — a column holding "one of the rates involved" would be
-- worse than none. What protects a historical figure is that conversion uses
-- the rate that was true at the time, not today's.
