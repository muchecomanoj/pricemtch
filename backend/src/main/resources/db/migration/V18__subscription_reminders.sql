-- Reminder emails before a plan expires, and a notice when it does.

-- 1. The two new email templates must be storable, or the admin editor fails
--    the first time someone customises one: template_key is checked against a
--    fixed list.
ALTER TABLE email_templates DROP CONSTRAINT IF EXISTS email_templates_template_key_check;
ALTER TABLE email_templates ADD CONSTRAINT email_templates_template_key_check
    CHECK (template_key IN ('CLIENT_ACTIVATION', 'VERIFICATION_CODE', 'PASSWORD_RESET',
                            'WELCOME', 'PLAN_CHANGE_PAYLINK',
                            'SUBSCRIPTION_EXPIRING', 'SUBSCRIPTION_EXPIRED'));

-- 2. What has been sent, so a reminder goes out once per billing period.
--
--    Keyed on the paid-through date rather than the tenant alone: renewing moves
--    that date, so the next period's reminders start fresh without clean-up.
--    The job also runs after every restart, and this is what stops it
--    re-sending the same reminder each time.
CREATE TABLE subscription_reminders (
    id           BIGSERIAL    PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    paid_through DATE         NOT NULL,
    kind         VARCHAR(20)  NOT NULL,   -- BEFORE_7, BEFORE_1, EXPIRED
    recipients   VARCHAR(1000),
    sent_at      TIMESTAMP(6) NOT NULL DEFAULT now(),
    CONSTRAINT uk_subscription_reminder UNIQUE (tenant_id, paid_through, kind)
);
