-- The landing page's three forms — Book a demo, Contact us, Newsletter — have
-- had tables since V1 but no endpoint, so nothing was ever written to them.
-- Wiring them up needs two things the baseline did not have: somewhere to
-- record that a submission was dealt with, and a way for a subscriber to leave
-- the newsletter without anyone being able to unsubscribe them.

-- Whether someone has acted on the enquiry. A landing-page lead is worthless if
-- two people answer it and a third assumes someone else did.
ALTER TABLE demo_requests
    ADD COLUMN handled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN handled_at TIMESTAMP(6),
    ADD COLUMN handled_by VARCHAR(150);

ALTER TABLE contact_messages
    ADD COLUMN handled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN handled_at TIMESTAMP(6),
    ADD COLUMN handled_by VARCHAR(150);

-- Unsubscribing is authorised by holding the token, not by naming the address:
-- an email address is public, so a plain /unsubscribe?email= would let anyone
-- remove anyone. Existing rows get one too, so no subscriber is left stranded.
ALTER TABLE newsletter_subscribers
    ADD COLUMN unsubscribe_token VARCHAR(64);

UPDATE newsletter_subscribers
-- md5(random()) rather than gen_random_uuid(), which is only built in from PG 13.
SET unsubscribe_token = md5(random()::text)
WHERE unsubscribe_token IS NULL;

ALTER TABLE newsletter_subscribers
    ALTER COLUMN unsubscribe_token SET NOT NULL;

ALTER TABLE newsletter_subscribers
    ADD CONSTRAINT uk_newsletter_unsubscribe_token UNIQUE (unsubscribe_token);

-- Every listing screen orders by newest first and filters on handled.
CREATE INDEX idx_demo_requests_created ON demo_requests (created_at DESC);
CREATE INDEX idx_demo_requests_handled ON demo_requests (handled);
CREATE INDEX idx_contact_messages_created ON contact_messages (created_at DESC);
CREATE INDEX idx_contact_messages_handled ON contact_messages (handled);
