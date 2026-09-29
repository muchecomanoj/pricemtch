-- Bounded automation for price recommendations (FR-REC-002).
--
-- Floors and ceilings existed only as request parameters: whoever called the
-- endpoint could pass them, and if they did not, nothing constrained the answer.
-- That is adequate for a human clicking "generate" and reading the result. It is
-- not adequate for automation, because there is nobody to notice a bad number —
-- which is why automated repricing could not responsibly be switched on at all.
--
-- A policy is stored rather than passed, resolved most-specific-first:
--
--     product + channel  →  product  →  tenant + channel  →  tenant
--
-- so a company-wide rule can be set once and a single product can still differ.
--
-- Two of these bounds guard against different failures. A floor stops a single
-- bad recommendation selling below cost. A maximum change per cycle stops a
-- *sequence* of individually reasonable recommendations walking the price down
-- over days — each step inside the floor, the destination nowhere near intended.
-- Cooldown does the same in time: it stops the system reacting to its own
-- previous move before the market has responded to it.

CREATE TABLE IF NOT EXISTS pricing_policies (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL,

    -- Null means "applies to everything below". A row with both null is the
    -- tenant default; naming a product and a channel makes it specific.
    product_id          BIGINT,
    marketplace         VARCHAR(20),

    -- Absolute floor, and a floor expressed as margin. Whichever is higher wins
    -- at the moment of calculation: a fixed floor cannot know today's costs, and
    -- a margin floor cannot express "never below what we paid".
    floor_price         NUMERIC(12,2),
    floor_margin_pct    NUMERIC(9,4),
    ceiling_price       NUMERIC(12,2),

    -- How far one cycle may move the price, as a percentage of the current one
    -- and as an absolute amount. The tighter of the two applies.
    max_change_pct      NUMERIC(9,4),
    max_change_amount   NUMERIC(12,2),

    -- Minimum hours between two published changes to the same product.
    cooldown_hours      INTEGER,

    -- Automation stays off unless switched on deliberately, and cannot be
    -- switched on where no bounds exist — see PricingPolicyService.
    auto_publish        BOOLEAN      NOT NULL DEFAULT FALSE,

    active              BOOLEAN      NOT NULL DEFAULT TRUE,

    created_at          TIMESTAMP(6) WITHOUT TIME ZONE,
    created_by          VARCHAR(255),
    updated_at          TIMESTAMP(6) WITHOUT TIME ZONE,
    updated_by          VARCHAR(255)
);

-- One policy per scope, so resolution is never ambiguous.
CREATE UNIQUE INDEX IF NOT EXISTS uq_pricing_policy_scope
    ON pricing_policies (tenant_id,
                         COALESCE(product_id, -1),
                         COALESCE(marketplace, '*'));

CREATE INDEX IF NOT EXISTS idx_pricing_policy_tenant
    ON pricing_policies (tenant_id, active);

-- What a recommendation's price was constrained by, and what it would have been.
--
-- Without this a capped recommendation is indistinguishable from an uncapped
-- one that happened to land on the same number, so nobody can tell whether the
-- policy is doing anything — or is quietly wrong.
ALTER TABLE price_recommendations
    ADD COLUMN IF NOT EXISTS unbounded_price NUMERIC(12,2);
ALTER TABLE price_recommendations
    ADD COLUMN IF NOT EXISTS bound_applied VARCHAR(30);

-- Rollback (FR-REC-002). The price this product was selling at immediately
-- before this recommendation was published, so a bad publish can be undone
-- without anyone having to remember what it replaced.
ALTER TABLE price_recommendations
    ADD COLUMN IF NOT EXISTS previous_price NUMERIC(12,2);
ALTER TABLE price_recommendations
    ADD COLUMN IF NOT EXISTS published_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE price_recommendations
    ADD COLUMN IF NOT EXISTS rolled_back_at TIMESTAMP(6) WITH TIME ZONE;
