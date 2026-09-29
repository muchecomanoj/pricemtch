--
-- V4 — record which client an import belongs to.
--
-- Bulk import runs on a background thread. The tenant is held in a ThreadLocal,
-- which does not cross the async boundary, so every product created by an
-- import was stamped with no owner. The import reported success and the rows
-- existed — they were simply invisible to the client who uploaded the file,
-- because product queries are scoped by tenant.
--
-- The code now captures the tenant on the request thread and re-establishes it
-- on the worker; this adds the column that carries it between the two.
--
-- Deliberately does NOT reassign products already orphaned by the bug. Which
-- client they belong to is not derivable from the data — the import job that
-- created them had no tenant either — and attributing a product to the wrong
-- client is worse than leaving it hidden. Those rows are repaired by hand,
-- per environment, by someone who knows who uploaded the file.
--

ALTER TABLE import_jobs ADD COLUMN IF NOT EXISTS tenant_id BIGINT;

ALTER TABLE import_jobs
    ADD CONSTRAINT fk_importjob_tenant FOREIGN KEY (tenant_id)
    REFERENCES tenants(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_importjob_tenant ON import_jobs(tenant_id);
