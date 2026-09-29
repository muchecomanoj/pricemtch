-- How tax relates to the listed price (Phase 2).
--
-- Tax was computed and returned, but always as a pass-through: added at
-- checkout, collected on the seller's behalf, never part of profit. That is US
-- sales tax and it is correct for the US.
--
-- It is wrong everywhere the price already includes the tax. A UK price of £120
-- with 20% VAT earns the seller £100, not £120, so every margin, break-even and
-- recommendation for a VAT market was overstated by the whole VAT rate — and
-- the error was invisible, because the arithmetic was consistent with itself.
--
-- PASS_THROUGH is the existing behaviour and stays the default, so no current
-- figure changes on this migration.

ALTER TABLE cost_profiles
    ADD COLUMN IF NOT EXISTS tax_treatment VARCHAR(20) NOT NULL DEFAULT 'PASS_THROUGH';

ALTER TABLE tenant_cost_models
    ADD COLUMN IF NOT EXISTS tax_treatment VARCHAR(20) NOT NULL DEFAULT 'PASS_THROUGH';
