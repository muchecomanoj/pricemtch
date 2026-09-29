-- Remove the price changes that never happened.
--
-- Amazon returns one competitive price PER CONDITION for an ASIN, each with a
-- CompetitivePriceId: id 1 is the New Buy Box, id 2 the Used one. For
-- B0DGHMNQ5Z it sends New $99.00 and Used $92.18 together; for B0DJMFS75S,
-- New $89.00 and Used $82.77.
--
-- The reader took whichever appeared first in the array, and that order is not
-- stable. Two calls a minute apart returned $99.00 and then $92.18 for a
-- listing nobody had repriced. Each flip was recorded as a real 7% move — and
-- those moves fire alerts, drive volatility and feed recommendations.
--
-- The reader now chooses deterministically (AmazonProductNormalizer), so no
-- further phantoms are written. This clears the ones already recorded.
--
-- WHAT IS REMOVED: Amazon money events that bounce between the SAME TWO VALUES
-- on one listing and field, three times or more, inside a week.
--
-- Three or more is the threshold on purpose. One move between two prices is an
-- ordinary change. Two — down and back up — is a plausible short sale, and
-- those are kept for that reason.
--
-- Frequency decides the rest. A seller who runs a weekend promotion alternates
-- between two prices for months, and that history is real. The reader flipping
-- between the New and Used competitive price alternates several times a DAY.
-- So a group must also average at least one alternation per day: 39 moves in
-- ten days qualifies, three moves in three weeks does not.
--
-- WHAT IS NOT: eBay events. eBay has no per-condition competitive price, so its
-- reversals have a different cause that has not been established, and deleting
-- them would be guessing.
--
-- Nothing is destroyed. Every removed row is copied to
-- price_change_events_removed first, with the reason, so this can be undone:
--
--   INSERT INTO price_change_events (SELECT <columns> FROM price_change_events_removed);

CREATE TABLE IF NOT EXISTS price_change_events_removed (
    LIKE price_change_events INCLUDING DEFAULTS
);

ALTER TABLE price_change_events_removed
    ADD COLUMN IF NOT EXISTS removed_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT NOW();
ALTER TABLE price_change_events_removed
    ADD COLUMN IF NOT EXISTS removed_reason VARCHAR(200);

-- Values are compared as NUMBERS, not as text. They are stored as strings, and
-- the same price is written both "89" and "89.00" depending on what Amazon
-- sent; a text comparison misses exactly the reversals this is looking for —
-- including B0DJMFS75S, the worst affected listing.
CREATE TEMP TABLE phantom_ids ON COMMIT DROP AS
WITH money AS (
    SELECT id, marketplace, storefront, marketplace_item_id, field,
           old_value::numeric AS old_amount, new_value::numeric AS new_amount,
           observed_at
      FROM price_change_events
     WHERE field IN ('PRICE', 'LANDED_PRICE')
       AND marketplace = 'AMAZON'
       AND old_value ~ '^[0-9]+(\.[0-9]+)?$'
       AND new_value ~ '^[0-9]+(\.[0-9]+)?$'
)
, oscillating AS (
    SELECT marketplace, storefront, marketplace_item_id, field,
           LEAST(old_amount, new_amount) AS lo,
           GREATEST(old_amount, new_amount) AS hi
      FROM money
     WHERE old_amount <> new_amount
     GROUP BY 1, 2, 3, 4, 5, 6
    HAVING COUNT(*) >= 3
       AND COUNT(*) >= GREATEST(1,
               EXTRACT(EPOCH FROM (MAX(observed_at) - MIN(observed_at))) / 86400)
)
SELECT m.id
  FROM money m
  JOIN oscillating o
    ON  o.marketplace         = m.marketplace
    AND o.storefront          = m.storefront
    AND o.marketplace_item_id = m.marketplace_item_id
    AND o.field               = m.field
    AND o.lo                  = LEAST(m.old_amount, m.new_amount)
    AND o.hi                  = GREATEST(m.old_amount, m.new_amount);

INSERT INTO price_change_events_removed
SELECT e.*, NOW(),
       'Phantom: Amazon New/Used competitive price read non-deterministically (V17)'
  FROM price_change_events e
 WHERE e.id IN (SELECT id FROM phantom_ids);

DELETE FROM price_change_events
 WHERE id IN (SELECT id FROM phantom_ids);

-- Change counts were inflated by the same phantoms. Recounted from what remains.
UPDATE marketplace_items m
   SET change_count = COALESCE((
        SELECT COUNT(*) FROM price_change_events e
         WHERE e.marketplace = m.marketplace
           AND e.storefront = m.storefront
           AND e.marketplace_item_id = m.marketplace_item_id), 0);
