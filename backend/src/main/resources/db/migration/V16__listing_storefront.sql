-- The storefront a listing belongs to.
--
-- A marketplace name does not identify a catalogue. amazon.com and amazon.ca are
-- separate shops, with separate prices, currencies and sellers, and the same ASIN
-- exists in both. Every table below identified a listing as marketplace + item id
-- only, which caused four separate faults:
--
--   * Attaching a listing found on amazon.ca at CA$140 re-fetched it without a
--     region, and stored amazon.com's US$144.29 in its place.
--   * marketplace_items was unique on (marketplace, item id), so the US and
--     Canadian versions of one ASIN were one row sharing one price history. That
--     history alternated between currencies.
--   * Each alternation was recorded as a price change. There are 46 of them: an
--     item "moving" from US$144 to CA$140 did not change price, it changed shop.
--   * competitor_listings was unique on (product, marketplace, item id), so a
--     product could never hold both versions; the second attach returned the first.
--
-- The storefront is an ISO 3166 two-letter country code (US, CA, GB), and is now
-- part of the identity of every listing, observation and change.

-- ---------------------------------------------------------------------------
-- 1. Columns
-- ---------------------------------------------------------------------------

ALTER TABLE competitor_listings     ADD COLUMN IF NOT EXISTS storefront VARCHAR(2);
ALTER TABLE marketplace_items       ADD COLUMN IF NOT EXISTS storefront VARCHAR(2);
ALTER TABLE listing_price_snapshots ADD COLUMN IF NOT EXISTS storefront VARCHAR(2);
ALTER TABLE price_change_events     ADD COLUMN IF NOT EXISTS storefront VARCHAR(2);

-- ---------------------------------------------------------------------------
-- 2. Backfill from what was actually observed
-- ---------------------------------------------------------------------------
--
-- Evidence in order of reliability: the page the listing lives on (URL host),
-- then the currency it was priced in, then US. Only USD, CAD and GBP exist in the
-- data at the time of writing, so the currency step is unambiguous.
--
-- This labels rows by where their data CAME FROM, not by what anyone intended.
-- A listing attached from a Canadian search but fetched from amazon.com is US
-- data at a US price, and is labelled US. Re-attach it to get the Canadian one.

CREATE OR REPLACE FUNCTION pg_temp.storefront_from_url(url TEXT) RETURNS VARCHAR(2) AS $$
    SELECT CASE
        WHEN url IS NULL THEN NULL
        -- Amazon: longest suffixes first, so amazon.com.au is not read as amazon.com.
        WHEN url ~* '://([^/]*\.)?amazon\.com\.au(/|$)' THEN 'AU'
        WHEN url ~* '://([^/]*\.)?amazon\.com\.mx(/|$)' THEN 'MX'
        WHEN url ~* '://([^/]*\.)?amazon\.com\.br(/|$)' THEN 'BR'
        WHEN url ~* '://([^/]*\.)?amazon\.com\.be(/|$)' THEN 'BE'
        WHEN url ~* '://([^/]*\.)?amazon\.com\.tr(/|$)' THEN 'TR'
        WHEN url ~* '://([^/]*\.)?amazon\.co\.uk(/|$)'  THEN 'GB'
        WHEN url ~* '://([^/]*\.)?amazon\.co\.jp(/|$)'  THEN 'JP'
        WHEN url ~* '://([^/]*\.)?amazon\.co\.za(/|$)'  THEN 'ZA'
        WHEN url ~* '://([^/]*\.)?amazon\.ca(/|$)'      THEN 'CA'
        WHEN url ~* '://([^/]*\.)?amazon\.de(/|$)'      THEN 'DE'
        WHEN url ~* '://([^/]*\.)?amazon\.fr(/|$)'      THEN 'FR'
        WHEN url ~* '://([^/]*\.)?amazon\.it(/|$)'      THEN 'IT'
        WHEN url ~* '://([^/]*\.)?amazon\.es(/|$)'      THEN 'ES'
        WHEN url ~* '://([^/]*\.)?amazon\.nl(/|$)'      THEN 'NL'
        WHEN url ~* '://([^/]*\.)?amazon\.se(/|$)'      THEN 'SE'
        WHEN url ~* '://([^/]*\.)?amazon\.pl(/|$)'      THEN 'PL'
        WHEN url ~* '://([^/]*\.)?amazon\.ae(/|$)'      THEN 'AE'
        WHEN url ~* '://([^/]*\.)?amazon\.sa(/|$)'      THEN 'SA'
        WHEN url ~* '://([^/]*\.)?amazon\.eg(/|$)'      THEN 'EG'
        WHEN url ~* '://([^/]*\.)?amazon\.in(/|$)'      THEN 'IN'
        WHEN url ~* '://([^/]*\.)?amazon\.sg(/|$)'      THEN 'SG'
        WHEN url ~* '://([^/]*\.)?amazon\.com(/|$)'     THEN 'US'
        -- eBay
        WHEN url ~* '://([^/]*\.)?ebay\.com\.au(/|$)'   THEN 'AU'
        WHEN url ~* '://([^/]*\.)?ebay\.co\.uk(/|$)'    THEN 'GB'
        WHEN url ~* '://([^/]*\.)?ebay\.ca(/|$)'        THEN 'CA'
        WHEN url ~* '://([^/]*\.)?ebay\.de(/|$)'        THEN 'DE'
        WHEN url ~* '://([^/]*\.)?ebay\.fr(/|$)'        THEN 'FR'
        WHEN url ~* '://([^/]*\.)?ebay\.it(/|$)'        THEN 'IT'
        WHEN url ~* '://([^/]*\.)?ebay\.es(/|$)'        THEN 'ES'
        WHEN url ~* '://([^/]*\.)?ebay\.in(/|$)'        THEN 'IN'
        WHEN url ~* '://([^/]*\.)?ebay\.com(/|$)'       THEN 'US'
        ELSE NULL
    END
$$ LANGUAGE SQL IMMUTABLE;

CREATE OR REPLACE FUNCTION pg_temp.storefront_from_currency(cur TEXT) RETURNS VARCHAR(2) AS $$
    SELECT CASE UPPER(cur)
        WHEN 'USD' THEN 'US'  WHEN 'CAD' THEN 'CA'  WHEN 'GBP' THEN 'GB'
        WHEN 'MXN' THEN 'MX'  WHEN 'BRL' THEN 'BR'  WHEN 'SEK' THEN 'SE'
        WHEN 'PLN' THEN 'PL'  WHEN 'TRY' THEN 'TR'  WHEN 'AED' THEN 'AE'
        WHEN 'SAR' THEN 'SA'  WHEN 'EGP' THEN 'EG'  WHEN 'INR' THEN 'IN'
        WHEN 'ZAR' THEN 'ZA'  WHEN 'JPY' THEN 'JP'  WHEN 'AUD' THEN 'AU'
        WHEN 'SGD' THEN 'SG'
        -- EUR is shared by several storefronts and is deliberately not guessed.
        ELSE NULL
    END
$$ LANGUAGE SQL IMMUTABLE;

UPDATE competitor_listings
   SET storefront = COALESCE(pg_temp.storefront_from_url(url),
                             pg_temp.storefront_from_currency(currency), 'US')
 WHERE storefront IS NULL;

UPDATE marketplace_items
   SET storefront = COALESCE(pg_temp.storefront_from_url(url),
                             pg_temp.storefront_from_currency(currency), 'US')
 WHERE storefront IS NULL;

-- Observations carry their own currency, which is the most direct evidence of
-- the shop they were read from — more reliable than the item row, whose single
-- currency is only the most recent of possibly several.
UPDATE listing_price_snapshots
   SET storefront = COALESCE(pg_temp.storefront_from_currency(currency), 'US')
 WHERE storefront IS NULL;

UPDATE price_change_events
   SET storefront = COALESCE(pg_temp.storefront_from_currency(currency), 'US')
 WHERE storefront IS NULL;

-- ---------------------------------------------------------------------------
-- 3. Remove the phantom price changes
-- ---------------------------------------------------------------------------
--
-- A money event whose previous observation was in a different currency did not
-- record a price moving. It recorded the shared history switching between two
-- shops. Left in place, every Price Changes screen and volatility figure would
-- keep reporting movements that never happened.

DELETE FROM price_change_events e
 WHERE e.field IN ('PRICE', 'SHIPPING', 'LANDED_PRICE')
   AND e.currency IS NOT NULL
   AND EXISTS (
       SELECT 1 FROM (
           SELECT s.currency
             FROM listing_price_snapshots s
            WHERE s.marketplace = e.marketplace
              AND s.marketplace_item_id = e.marketplace_item_id
              AND s.observed_at < e.observed_at
            ORDER BY s.observed_at DESC
            LIMIT 1
       ) prev
       WHERE prev.currency IS NOT NULL AND prev.currency <> e.currency
   );

-- ---------------------------------------------------------------------------
-- 4. Storefront becomes part of every key
-- ---------------------------------------------------------------------------
--
-- Before the split below, not after: the old (marketplace, item id) key forbids
-- exactly the second row the split exists to create. The new keys are strictly
-- looser than the old ones, so no existing row can violate them.

ALTER TABLE competitor_listings     ALTER COLUMN storefront SET NOT NULL;
ALTER TABLE marketplace_items       ALTER COLUMN storefront SET NOT NULL;
ALTER TABLE listing_price_snapshots ALTER COLUMN storefront SET NOT NULL;
ALTER TABLE price_change_events     ALTER COLUMN storefront SET NOT NULL;

ALTER TABLE competitor_listings DROP CONSTRAINT IF EXISTS uk_competitor_listing;
DROP INDEX IF EXISTS uk_competitor_listing;
CREATE UNIQUE INDEX uk_competitor_listing
    ON competitor_listings (product_id, marketplace, storefront, marketplace_item_id);

ALTER TABLE marketplace_items DROP CONSTRAINT IF EXISTS uk_marketplace_item;
DROP INDEX IF EXISTS uk_marketplace_item;
CREATE UNIQUE INDEX uk_marketplace_item
    ON marketplace_items (marketplace, storefront, marketplace_item_id);

DROP INDEX IF EXISTS idx_price_snapshot_item;
CREATE INDEX idx_price_snapshot_item
    ON listing_price_snapshots (marketplace, storefront, marketplace_item_id, observed_at);

DROP INDEX IF EXISTS idx_price_change_item;
CREATE INDEX idx_price_change_item
    ON price_change_events (marketplace, storefront, marketplace_item_id, observed_at DESC);

-- ---------------------------------------------------------------------------
-- 5. Split tracked items that held more than one storefront's history
-- ---------------------------------------------------------------------------
--
-- Each existing item row now describes the storefront of its latest observation.
-- The other storefront's history has no row of its own; create one from that
-- storefront's most recent observation, so both appear on Tracked Items and each
-- chart opens on its own series.

INSERT INTO marketplace_items (
        marketplace, storefront, marketplace_item_id, title, brand, url, seller,
        condition, currency, item_price, shipping, landed_price, availability,
        identifiers, first_seen_at, last_seen_at, last_changed_at,
        observation_count, change_count, created_at, updated_at)
SELECT s.marketplace, s.storefront, s.marketplace_item_id,
       sibling.title, sibling.brand,
       NULL,                         -- the sibling's URL is the other shop's page
       NULL, sibling.condition,
       s.currency, s.item_price, s.shipping, s.landed_price,
       NULL, sibling.identifiers,
       stats.first_seen, s.observed_at, s.observed_at,
       stats.observations, 0,
       NOW(), NOW()
  FROM (
        SELECT DISTINCT ON (marketplace, storefront, marketplace_item_id) *
          FROM listing_price_snapshots
         WHERE marketplace_item_id IS NOT NULL
         ORDER BY marketplace, storefront, marketplace_item_id, observed_at DESC
       ) s
  JOIN (
        SELECT marketplace, storefront, marketplace_item_id,
               MIN(observed_at) AS first_seen, COUNT(*) AS observations
          FROM listing_price_snapshots
         WHERE marketplace_item_id IS NOT NULL
         GROUP BY 1, 2, 3
       ) stats USING (marketplace, storefront, marketplace_item_id)
  JOIN marketplace_items sibling
    ON sibling.marketplace = s.marketplace
   AND sibling.marketplace_item_id = s.marketplace_item_id
 WHERE NOT EXISTS (
        SELECT 1 FROM marketplace_items m
         WHERE m.marketplace = s.marketplace
           AND m.storefront = s.storefront
           AND m.marketplace_item_id = s.marketplace_item_id);

-- Change counts were accumulated across both shops, phantoms included.
-- Recounted from the events that remain.
UPDATE marketplace_items m
   SET change_count = COALESCE((
        SELECT COUNT(*) FROM price_change_events e
         WHERE e.marketplace = m.marketplace
           AND e.storefront = m.storefront
           AND e.marketplace_item_id = m.marketplace_item_id), 0);

-- ---------------------------------------------------------------------------
-- 6. Empty the search-result cache
-- ---------------------------------------------------------------------------
--
-- It maps a query to bare item ids, which no longer identify a listing. Rather
-- than guess which storefront each cached id meant, drop the cache: the next
-- search of each query fetches again and records storefronts properly. It is a
-- cache, so nothing is lost but one API call per query.

DELETE FROM marketplace_search_cache;
