-- Which country's storefront a bulk search looks in.
--
-- A single search has always taken a region; a bulk upload had no way to say
-- one, so every row searched the SP-API default (US) and a file of UK products
-- came back empty. The job carries the region chosen at upload; a row carries
-- the one its own Region column named, for a file that mixes countries.
--
-- Nullable, and null keeps the old behaviour exactly: no region means the
-- planner hunts its own authorised marketplaces, as it did before this column
-- existed. Existing jobs are history and are left alone.
ALTER TABLE bulk_search_jobs ADD COLUMN region VARCHAR(5);

ALTER TABLE bulk_search_rows ADD COLUMN input_region VARCHAR(5);
