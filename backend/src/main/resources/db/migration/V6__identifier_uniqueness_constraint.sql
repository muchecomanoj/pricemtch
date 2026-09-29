-- Enforce "one identifier, one product per tenant" in the database.
--
-- The rule is already checked in ProductServiceImpl, but a check-then-insert in
-- application code is a race: two requests can both read "free" before either
-- writes. The window is small and a person will rarely hit it — a CSV import
-- running while someone saves a product will.
--
-- The identifiers table has no tenant of its own; the owning product holds it,
-- and Postgres cannot enforce uniqueness across a join. So the tenant is
-- denormalised onto the row, the same way competitor_listings already does it,
-- and the constraint becomes a plain unique index.

ALTER TABLE product_identifiers
    ADD COLUMN IF NOT EXISTS tenant_id BIGINT;

UPDATE product_identifiers i
   SET tenant_id = p.tenant_id
  FROM products p
 WHERE p.id = i.product_id
   AND i.tenant_id IS DISTINCT FROM p.tenant_id;

-- Only the globally-unique types. MPN is a manufacturer's part number and is
-- legitimately shared across sellers and listings, so it is excluded — forcing
-- it unique would reject honest data.
CREATE UNIQUE INDEX IF NOT EXISTS uq_identifier_tenant_type_value
    ON product_identifiers (tenant_id, type, normalized_value)
 WHERE type IN ('ASIN', 'UPC', 'EAN', 'GTIN')
   AND tenant_id IS NOT NULL
   AND normalized_value IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_identifier_tenant
    ON product_identifiers (tenant_id);
