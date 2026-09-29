-- Listing links need the same room as the tracked-item links they mirror.
--
-- eBay returns links with its own tracking parameters attached — 598 characters
-- for a link whose useful part is 38 — and 600 was not enough. The insert failed,
-- and because a failed statement poisons the surrounding transaction, one long
-- link cost the user the whole search. Six searches failed this way today.
--
-- marketplace_items.url is already 1000; this brings the pair into line. The
-- application also drops tracking parameters from any link that still would not
-- fit, so this width is headroom rather than the only defence.

ALTER TABLE competitor_listings ALTER COLUMN url TYPE VARCHAR(1000);
