-- The merchant behind a tracked listing, and the merchant behind a price change.
--
-- marketplace_items.seller has always held the BRAND on Amazon ("Apple",
-- "Anker") because the catalogue API returns no merchant name. That made the
-- column useless for the question it looks like it answers, and left a real
-- effect invisible: one AirPods listing recorded 52 "price changes" in six days,
-- bouncing between exactly two values. That was not a competitor repricing
-- fifty-two times. It was two merchants trading the Buy Box, and every handover
-- was filed as a price cut.
--
-- The merchant id lives in Amazon's offers endpoint, which is a second call. It
-- is fetched only when a price actually moved — the only moment the answer
-- changes anything — so a steady product costs nothing extra.

ALTER TABLE marketplace_items ADD COLUMN IF NOT EXISTS seller_id VARCHAR(64);

-- Amazon publishes no seller name, only an id. Named so nobody expects one.
COMMENT ON COLUMN marketplace_items.seller_id IS
    'Merchant id of the current Buy Box holder. Amazon identifies sellers by id, not name.';

ALTER TABLE price_change_events ADD COLUMN IF NOT EXISTS seller_id VARCHAR(64);
ALTER TABLE price_change_events ADD COLUMN IF NOT EXISTS previous_seller_id VARCHAR(64);

-- Lets a reader tell "the same seller lowered their price" from "a different
-- seller took the Buy Box at a lower price" — two facts that look identical in
-- a price series and lead to opposite decisions.
ALTER TABLE price_change_events ADD COLUMN IF NOT EXISTS seller_changed BOOLEAN;
