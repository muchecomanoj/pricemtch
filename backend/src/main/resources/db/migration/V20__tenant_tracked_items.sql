-- Which company has seen which marketplace listing.
--
-- A listing's price history is recorded once for the whole platform — what
-- Amazon charged is a fact about Amazon, and recording it once per client
-- would multiply the work for nothing. But the Price Changes page listed every
-- tracked listing to every company: a client created five minutes earlier saw
-- 55 changes on products other clients had been researching.
--
-- This records the link. A company's feed then shows changes on listings it
-- searched (this table) or attached to one of its products (competitor_listings).
--
-- Not backfilled from past searches: nothing recorded who ran them, so there
-- is no honest way to credit them. Listings already attached to a company's
-- products are covered by competitor_listings without any copy.

CREATE TABLE tenant_tracked_items (
    id                  BIGSERIAL    PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    marketplace         VARCHAR(20)  NOT NULL,
    storefront          VARCHAR(2)   NOT NULL,
    marketplace_item_id VARCHAR(100) NOT NULL,
    first_seen_at       TIMESTAMP(6) NOT NULL DEFAULT now(),
    last_seen_at        TIMESTAMP(6) NOT NULL DEFAULT now(),
    CONSTRAINT uk_tenant_tracked_item UNIQUE (tenant_id, marketplace, storefront, marketplace_item_id)
);

-- The feed asks "does this company have this listing?" once per change row.
CREATE INDEX idx_tenant_tracked_item_listing
    ON tenant_tracked_items (marketplace, storefront, marketplace_item_id, tenant_id);
