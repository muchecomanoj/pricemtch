-- Dated cost profiles (Phase 2).
--
-- A product had exactly one cost profile, enforced by a unique constraint on
-- product_id. Editing it overwrote the previous costs, so:
--
--   * last quarter's margin was recomputed with this quarter's freight, and the
--     number quietly changed every time somebody updated a cost;
--   * a price approved against $4.10 landed cost could not be explained later,
--     because the $4.10 no longer existed anywhere;
--   * a supplier increase could not be entered ahead of the date it takes
--     effect without immediately falsifying today's figures.
--
-- The column valid_from already existed and was recorded but never used. It now
-- carries the profile's identity: one row per product per effective date, and a
-- calculation picks the row that was in force on the date it is asking about.

-- Existing rows have been in force since before anything the system records.
-- A sentinel rather than NULL, because NULL cannot participate in the unique
-- constraint below and "unknown start" and "always" are different claims.
UPDATE cost_profiles SET valid_from = DATE '1900-01-01' WHERE valid_from IS NULL;

ALTER TABLE cost_profiles ALTER COLUMN valid_from SET NOT NULL;
ALTER TABLE cost_profiles ALTER COLUMN valid_from SET DEFAULT DATE '1900-01-01';

-- One profile per product per effective date. Replaces the constraint that
-- allowed only one profile per product outright.
ALTER TABLE cost_profiles DROP CONSTRAINT IF EXISTS uk_cost_profile_product;
DROP INDEX IF EXISTS uk_cost_profile_product;

CREATE UNIQUE INDEX IF NOT EXISTS uq_cost_profile_product_from
    ON cost_profiles (product_id, valid_from);

-- The lookup every calculation makes: the newest profile that had already taken
-- effect on a given date.
CREATE INDEX IF NOT EXISTS idx_cost_profile_effective
    ON cost_profiles (product_id, valid_from DESC);
