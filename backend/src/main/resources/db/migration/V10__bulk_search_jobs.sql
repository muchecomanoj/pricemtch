-- Bulk marketplace search: one uploaded file, one search per row (§13.1).
--
-- Deliberately separate from product_import_jobs. That import CREATES products
-- and never calls a marketplace; this one calls marketplaces and creates
-- nothing. Sharing a table would mean a status column that means two different
-- things and a "success" count that sometimes counts products and sometimes
-- counts listings.
--
-- Nothing found here enters the catalogue on its own. Each row is reviewed and
-- saved by hand, because a bulk search over an unfamiliar list is exactly where
-- a wrong match is least likely to be noticed.
--
-- These jobs are necessarily slow and that is not a defect: judging costs about
-- 2400 tokens against an 8000/minute budget, so roughly three rows a minute.
-- A hundred-row file takes over half an hour. Hence a background job with
-- honest progress rather than a request that blocks.

CREATE TABLE IF NOT EXISTS bulk_search_jobs (
    id               BIGSERIAL PRIMARY KEY,
    tenant_id        BIGINT,

    file_name        VARCHAR(255),
    file_type        VARCHAR(10),

    -- PENDING, PROCESSING, COMPLETED, COMPLETED_WITH_ERRORS, FAILED, CANCELLED
    status           VARCHAR(30)  NOT NULL,

    -- Whether the model ruled on each row's results. Recorded per job because
    -- it changes both how long the job takes and how far its results can be
    -- trusted, and a reader months later needs to know which it was.
    judge            BOOLEAN      NOT NULL DEFAULT TRUE,

    destination_country     VARCHAR(2),
    destination_postal_code VARCHAR(20),

    total_rows       INTEGER      NOT NULL DEFAULT 0,
    processed_rows   INTEGER      NOT NULL DEFAULT 0,
    found_rows       INTEGER      NOT NULL DEFAULT 0,
    empty_rows       INTEGER      NOT NULL DEFAULT 0,
    error_rows       INTEGER      NOT NULL DEFAULT 0,

    message          VARCHAR(500),
    started_at       TIMESTAMP(6) WITHOUT TIME ZONE,
    finished_at      TIMESTAMP(6) WITHOUT TIME ZONE,

    created_at       TIMESTAMP(6) WITHOUT TIME ZONE,
    created_by       VARCHAR(255),
    updated_at       TIMESTAMP(6) WITHOUT TIME ZONE,
    updated_by       VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_bulk_search_tenant
    ON bulk_search_jobs (tenant_id, created_at DESC);

-- One row of the uploaded file, and what the marketplaces said about it.
CREATE TABLE IF NOT EXISTS bulk_search_rows (
    id               BIGSERIAL PRIMARY KEY,
    job_id           BIGINT       NOT NULL,
    row_number       INTEGER      NOT NULL,

    -- What the file asked for, kept verbatim so a result can always be traced
    -- back to the line that produced it.
    input_sku        VARCHAR(100),
    input_title      VARCHAR(500),
    input_brand      VARCHAR(150),
    input_asin       VARCHAR(20),
    input_upc        VARCHAR(20),
    input_ean        VARCHAR(20),
    input_gtin       VARCHAR(20),
    input_mpn        VARCHAR(100),

    -- What was actually searched for, and how it was chosen.
    resolved_query   VARCHAR(500),
    resolved_identifier_type VARCHAR(20),

    -- PENDING, FOUND, NONE_MATCHED, NO_LISTINGS, NOT_JUDGED, ERROR
    status           VARCHAR(30)  NOT NULL,

    match_count      INTEGER      NOT NULL DEFAULT 0,
    rejected_count   INTEGER      NOT NULL DEFAULT 0,

    -- The judged listings, as the same JSON the single-search endpoint returns.
    -- Stored rather than re-fetched: re-running the search to render a results
    -- page would spend marketplace quota again and could return different
    -- prices, so the page would disagree with the job that produced it.
    matches_json     TEXT,
    rejected_json    TEXT,

    message          VARCHAR(1000),
    correlation_id   VARCHAR(64),

    -- Set when the user saves this row into the catalogue, so a second click
    -- shows what it produced rather than creating a duplicate product.
    saved_product_id BIGINT,

    searched_at      TIMESTAMP(6) WITH TIME ZONE,

    created_at       TIMESTAMP(6) WITHOUT TIME ZONE,
    created_by       VARCHAR(255),
    updated_at       TIMESTAMP(6) WITHOUT TIME ZONE,
    updated_by       VARCHAR(255),

    CONSTRAINT fk_bulk_row_job FOREIGN KEY (job_id)
        REFERENCES bulk_search_jobs (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_bulk_row_job ON bulk_search_rows (job_id, row_number);
