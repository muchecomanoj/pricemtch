-- A SKU is a company's own code for its own product, so it must be unique
-- within a company — not across the whole platform.
--
-- Unique platform-wide, one client's SKU blocked every other client from using
-- it, and the "already exists" error told them someone else had it. The ingest
-- tools, which look products up by SKU, could also hand one company another
-- company's product.
--
-- Case-insensitive, matching how the importer already compares SKUs: "pat-1001"
-- and "PAT-1001" are the same code to a person, and should be to the database.

ALTER TABLE products DROP CONSTRAINT IF EXISTS uk_products_sku;

CREATE UNIQUE INDEX uk_products_tenant_sku ON products (tenant_id, lower(sku));
