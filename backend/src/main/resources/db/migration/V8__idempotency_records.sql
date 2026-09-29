-- Remembers what an action returned, so repeating it returns the same answer
-- instead of doing the work twice (FR API standards, §12.2).
--
-- The actions worth protecting all spend something that cannot be refunded: a
-- marketplace call against a shared quota, an AI call against a per-minute token
-- budget, or a change to a product's selling price. A double-click or a browser
-- retry on any of those is indistinguishable from a genuine second request at
-- the HTTP layer, so the decision has to be recorded.

CREATE TABLE IF NOT EXISTS idempotency_records (
    id             BIGSERIAL PRIMARY KEY,

    -- Either the client's Idempotency-Key header, or a fingerprint the server
    -- derives from the tenant, endpoint and request body when no header is sent.
    idem_key       VARCHAR(200) NOT NULL,

    -- Which action this belongs to, so the same key used against two endpoints
    -- cannot collide.
    scope          VARCHAR(80)  NOT NULL,

    tenant_id      BIGINT,

    -- The serialised response of the first successful call. Null while the first
    -- call is still running — a concurrent duplicate sees the row and waits for
    -- the outcome rather than starting its own.
    response_json  TEXT,

    status         VARCHAR(20)  NOT NULL DEFAULT 'IN_PROGRESS',

    created_at     TIMESTAMP,
    created_by     VARCHAR(255),
    updated_at     TIMESTAMP,
    updated_by     VARCHAR(255)
);

-- The claim: whoever inserts first owns the work. A second request with the
-- same key fails this constraint, which is how it learns it is a duplicate.
CREATE UNIQUE INDEX IF NOT EXISTS uq_idempotency_key
    ON idempotency_records (scope, idem_key);

CREATE INDEX IF NOT EXISTS idx_idempotency_created
    ON idempotency_records (created_at);
