-- target: tenant
-- Allows CURRENT_BATCH_SELLING_PRICE as an inventory pricing policy and
-- normalizes both inventory columns to VARCHAR (same style as V20260628_01).
-- Fresh installs from tenant_schema.sql define these as ENUM, which would
-- reject the new value; VARCHAR keeps every past/future policy value valid
-- without a column ALTER. Values are still gated in CompanyService.
ALTER TABLE companies
    MODIFY COLUMN inventory_consumption_method VARCHAR(32) NOT NULL DEFAULT 'FIFO',
    MODIFY COLUMN inventory_pricing_policy VARCHAR(64) NOT NULL DEFAULT 'LATEST_BATCH_SELLING_PRICE';
