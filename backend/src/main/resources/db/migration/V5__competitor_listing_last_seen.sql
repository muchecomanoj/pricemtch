-- When a competitor listing was last returned by a search, as opposed to when
-- its price was last read from the marketplace.
--
-- source_timestamp cannot answer "what did the last run find". A run served from
-- the shared cache re-uses an earlier fetch and leaves that column alone, so a
-- monitor reporting "5 listings last run" could show none with a recent
-- timestamp. This column moves on every run that returns the row, whether the
-- data was fetched or re-used.
--
-- It also makes the opposite question answerable: a listing whose last_seen_at
-- has stopped moving is one the marketplace no longer returns.

ALTER TABLE competitor_listings
    ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMP WITH TIME ZONE;

-- Existing rows: the best evidence we have of when each was last returned is
-- when its price was last stamped.
UPDATE competitor_listings
   SET last_seen_at = source_timestamp
 WHERE last_seen_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_competitor_last_seen
    ON competitor_listings (product_id, last_seen_at);
