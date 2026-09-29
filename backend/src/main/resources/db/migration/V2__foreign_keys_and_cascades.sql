--
-- V2 — referential integrity.
--
-- Until now the database held almost no foreign keys: rows referenced tenants,
-- products and listings by id, but nothing enforced that the parent existed.
-- Two consequences showed up in practice:
--
--   * deleting a product left its competitor listings behind, invisible and
--     unowned;
--   * removing a client took 27 DELETE statements in a specific order, and any
--     omission left debris that no screen would ever show again.
--
-- Adding the constraints with ON DELETE CASCADE makes the database enforce what
-- the code assumed, and reduces "remove a client" to a single statement.
--
-- Note on scope: cascading from tenants deletes that client's payments and
-- subscription history along with everything else. That is the intended
-- behaviour here — a removed client leaves no residue — but it does mean the
-- billing trail goes with them. Deactivate rather than delete a live client.
--

-- ---------------------------------------------------------------
-- 1. Clear existing orphans.
--    A constraint cannot be added while rows violate it, and these rows are
--    already unreachable through the application.
-- ---------------------------------------------------------------

DELETE FROM listing_price_snapshots s
 WHERE NOT EXISTS (SELECT 1 FROM competitor_listings c WHERE c.id = s.listing_id);

DELETE FROM competitor_listings c
 WHERE NOT EXISTS (SELECT 1 FROM products p WHERE p.id = c.product_id);

DELETE FROM cost_profiles x
 WHERE NOT EXISTS (SELECT 1 FROM products p WHERE p.id = x.product_id);

DELETE FROM price_recommendations x
 WHERE NOT EXISTS (SELECT 1 FROM products p WHERE p.id = x.product_id);

DELETE FROM profitability_snapshots x
 WHERE NOT EXISTS (SELECT 1 FROM products p WHERE p.id = x.product_id);

DELETE FROM search_jobs x
 WHERE NOT EXISTS (SELECT 1 FROM products p WHERE p.id = x.product_id);

DELETE FROM alert_rules x
 WHERE NOT EXISTS (SELECT 1 FROM products p WHERE p.id = x.product_id);

DELETE FROM monitors x
 WHERE NOT EXISTS (SELECT 1 FROM products p WHERE p.id = x.product_id);

-- Rows pointing at tenants that no longer exist. Includes three payments
-- recorded as PAID against tenants deleted long ago — kept in the CSV backups
-- taken before this migration, but unreachable and unattributable in place.
DELETE FROM payments             x WHERE x.tenant_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM tenants t WHERE t.id = x.tenant_id);
DELETE FROM subscription_history x WHERE x.tenant_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM tenants t WHERE t.id = x.tenant_id);
DELETE FROM notifications        x WHERE x.tenant_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM tenants t WHERE t.id = x.tenant_id);
DELETE FROM audit_logs           x WHERE x.tenant_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM tenants t WHERE t.id = x.tenant_id);
DELETE FROM activation_tokens    x WHERE x.tenant_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM tenants t WHERE t.id = x.tenant_id);
DELETE FROM tenant_cost_models   x WHERE x.tenant_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM tenants t WHERE t.id = x.tenant_id);
DELETE FROM tenant_email_configs x WHERE x.tenant_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM tenants t WHERE t.id = x.tenant_id);
DELETE FROM tenant_notification_channels x WHERE x.tenant_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM tenants t WHERE t.id = x.tenant_id);
DELETE FROM search_history       x WHERE x.tenant_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM tenants t WHERE t.id = x.tenant_id);
DELETE FROM ai_executions        x WHERE x.tenant_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM tenants t WHERE t.id = x.tenant_id);
DELETE FROM users                x WHERE x.tenant_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM tenants t WHERE t.id = x.tenant_id);
DELETE FROM products             x WHERE x.tenant_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM tenants t WHERE t.id = x.tenant_id);
DELETE FROM competitor_listings  x WHERE x.tenant_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM tenants t WHERE t.id = x.tenant_id);
DELETE FROM monitors             x WHERE x.tenant_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM tenants t WHERE t.id = x.tenant_id);
DELETE FROM alert_rules          x WHERE x.tenant_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM tenants t WHERE t.id = x.tenant_id);

DELETE FROM refresh_tokens        x WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.id = x.user_id);
DELETE FROM password_reset_tokens x WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.id = x.user_id);
DELETE FROM import_errors         x WHERE NOT EXISTS (SELECT 1 FROM import_jobs j WHERE j.id = x.import_job_id);

-- ---------------------------------------------------------------
-- 2. Replace the existing parent links so they cascade.
--    Hibernate generated these with NO ACTION, which is why deleting a product
--    failed rather than cleaning up after itself. Names were auto-generated,
--    so they are dropped by lookup rather than by literal name.
-- ---------------------------------------------------------------

DO $$
DECLARE
    c RECORD;
BEGIN
    FOR c IN
        SELECT conrelid::regclass AS tbl, conname
          FROM pg_constraint
         WHERE contype = 'f'
           AND conrelid::regclass::text IN (
               'product_identifiers', 'product_images', 'product_attributes',
               'refresh_tokens', 'password_reset_tokens', 'import_errors')
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', c.tbl, c.conname);
    END LOOP;
END $$;

-- ---------------------------------------------------------------
-- 3. Add the constraints.
--    Every one cascades: these are all rows that exist only to describe their
--    parent, so outliving it has no meaning.
-- ---------------------------------------------------------------

-- children of products
ALTER TABLE product_identifiers    ADD CONSTRAINT fk_identifier_product   FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE;
ALTER TABLE product_images         ADD CONSTRAINT fk_image_product        FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE;
ALTER TABLE product_attributes     ADD CONSTRAINT fk_attribute_product    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE;
ALTER TABLE competitor_listings    ADD CONSTRAINT fk_listing_product      FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE;
ALTER TABLE cost_profiles          ADD CONSTRAINT fk_costprofile_product  FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE;
ALTER TABLE price_recommendations  ADD CONSTRAINT fk_recommendation_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE;
ALTER TABLE profitability_snapshots ADD CONSTRAINT fk_profitsnap_product  FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE;
ALTER TABLE search_jobs            ADD CONSTRAINT fk_searchjob_product    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE;
ALTER TABLE alert_rules            ADD CONSTRAINT fk_alertrule_product    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE;
ALTER TABLE monitors               ADD CONSTRAINT fk_monitor_product      FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE;

-- price history belongs to the listing it was observed on
ALTER TABLE listing_price_snapshots ADD CONSTRAINT fk_snapshot_listing    FOREIGN KEY (listing_id) REFERENCES competitor_listings(id) ON DELETE CASCADE;

-- children of users
ALTER TABLE refresh_tokens         ADD CONSTRAINT fk_refreshtoken_user    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE password_reset_tokens  ADD CONSTRAINT fk_resettoken_user      FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

-- children of import jobs
ALTER TABLE import_errors          ADD CONSTRAINT fk_importerror_job      FOREIGN KEY (import_job_id) REFERENCES import_jobs(id) ON DELETE CASCADE;

-- everything owned by a client
ALTER TABLE users                  ADD CONSTRAINT fk_user_tenant          FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE;
ALTER TABLE products               ADD CONSTRAINT fk_product_tenant       FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE;
ALTER TABLE competitor_listings    ADD CONSTRAINT fk_listing_tenant       FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE;
ALTER TABLE monitors               ADD CONSTRAINT fk_monitor_tenant       FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE;
ALTER TABLE alert_rules            ADD CONSTRAINT fk_alertrule_tenant     FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE;
ALTER TABLE notifications          ADD CONSTRAINT fk_notification_tenant  FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE;
ALTER TABLE payments               ADD CONSTRAINT fk_payment_tenant       FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE;
ALTER TABLE subscription_history   ADD CONSTRAINT fk_subhistory_tenant    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE;
ALTER TABLE activation_tokens      ADD CONSTRAINT fk_activation_tenant    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE;
ALTER TABLE audit_logs             ADD CONSTRAINT fk_auditlog_tenant      FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE;
ALTER TABLE tenant_cost_models     ADD CONSTRAINT fk_costmodel_tenant     FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE;
ALTER TABLE tenant_email_configs   ADD CONSTRAINT fk_emailconfig_tenant   FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE;
ALTER TABLE tenant_notification_channels ADD CONSTRAINT fk_notifchannels_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE;
ALTER TABLE search_history         ADD CONSTRAINT fk_searchhistory_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE;
ALTER TABLE ai_executions          ADD CONSTRAINT fk_aiexecution_tenant   FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE;

-- ---------------------------------------------------------------
-- 4. Indexes on the foreign key columns.
--    PostgreSQL indexes the parent side automatically but not the child, and
--    every cascading delete scans the child by that column.
-- ---------------------------------------------------------------

CREATE INDEX IF NOT EXISTS idx_listing_product        ON competitor_listings(product_id);
CREATE INDEX IF NOT EXISTS idx_snapshot_listing_fk    ON listing_price_snapshots(listing_id);
CREATE INDEX IF NOT EXISTS idx_costprofile_product    ON cost_profiles(product_id);
CREATE INDEX IF NOT EXISTS idx_recommendation_product ON price_recommendations(product_id);
CREATE INDEX IF NOT EXISTS idx_profitsnap_product     ON profitability_snapshots(product_id);
CREATE INDEX IF NOT EXISTS idx_searchjob_product      ON search_jobs(product_id);
CREATE INDEX IF NOT EXISTS idx_alertrule_product      ON alert_rules(product_id);
CREATE INDEX IF NOT EXISTS idx_user_tenant            ON users(tenant_id);
CREATE INDEX IF NOT EXISTS idx_product_tenant         ON products(tenant_id);
CREATE INDEX IF NOT EXISTS idx_payment_tenant         ON payments(tenant_id);
CREATE INDEX IF NOT EXISTS idx_subhistory_tenant      ON subscription_history(tenant_id);
CREATE INDEX IF NOT EXISTS idx_notification_tenant    ON notifications(tenant_id);
