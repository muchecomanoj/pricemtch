--
-- V3 — add PENDING_APPROVAL to the recommendation workflow.
--
-- FR-REC-002 asks for six states: draft, pending approval, approved, rejected,
-- expired and published. Five existed; without the sixth there was no queue of
-- price changes waiting on a human, which is the point of requiring approval.
--
-- The status column carries a CHECK constraint listing the permitted values, so
-- the enum alone is not enough — an insert would be refused by the database.
-- Dropped and recreated by lookup rather than by literal name, because the
-- original was auto-generated.
--

DO $$
DECLARE
    c RECORD;
BEGIN
    FOR c IN
        SELECT conname
          FROM pg_constraint
         WHERE conrelid = 'price_recommendations'::regclass
           AND contype = 'c'
           AND pg_get_constraintdef(oid) LIKE '%status%'
    LOOP
        EXECUTE format('ALTER TABLE price_recommendations DROP CONSTRAINT %I', c.conname);
    END LOOP;
END $$;

ALTER TABLE price_recommendations
    ADD CONSTRAINT price_recommendations_status_check
    CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'PUBLISHED', 'REJECTED', 'EXPIRED'));

-- The approver's queue is read by status, and it is the one view that will be
-- opened repeatedly and expected to be instant.
CREATE INDEX IF NOT EXISTS idx_recommendation_status ON price_recommendations(status);
