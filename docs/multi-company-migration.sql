-- Review constraint names in your MySQL database before running DROP statements.
-- Run after a full backup.

ALTER TABLE companies ADD COLUMN code VARCHAR(20) NULL;

UPDATE companies
SET code = UPPER(REGEXP_REPLACE(name, '[^A-Za-z0-9]', ''))
WHERE code IS NULL OR code = '';

ALTER TABLE companies MODIFY code VARCHAR(20) NOT NULL;
ALTER TABLE companies ADD CONSTRAINT uk_companies_code UNIQUE (code);

ALTER TABLE companies ADD COLUMN database_name VARCHAR(128) NULL;

UPDATE users SET role = 'OWNER' WHERE role = 'COMPANY_ADMIN';
UPDATE users SET role = 'USER' WHERE role = 'STAFF';

-- SUPER_ADMIN rows must be assigned to a company as OWNER or removed before this step.
-- UPDATE users SET company_id = <company_id>, role = 'OWNER' WHERE role = 'SUPER_ADMIN';

ALTER TABLE users MODIFY company_id BIGINT NOT NULL;

ALTER TABLE invoice_items ADD COLUMN company_id BIGINT NULL;
UPDATE invoice_items ii
JOIN invoices i ON i.id = ii.invoice_id
SET ii.company_id = i.company_id
WHERE ii.company_id IS NULL;
ALTER TABLE invoice_items MODIFY company_id BIGINT NOT NULL;
ALTER TABLE invoice_items
    ADD CONSTRAINT fk_invoice_items_company FOREIGN KEY (company_id) REFERENCES companies(id);

-- Replace the old invoice_no unique key with tenant-scoped uniqueness.
-- ALTER TABLE invoices DROP INDEX <old_invoice_no_unique_index_name>;
ALTER TABLE invoices ADD CONSTRAINT uk_invoices_company_invoice_no UNIQUE (company_id, invoice_no);

CREATE INDEX idx_users_company_id ON users(company_id);
CREATE INDEX idx_customers_company_id ON customers(company_id);
CREATE INDEX idx_products_company_id ON products(company_id);
CREATE INDEX idx_invoices_company_id ON invoices(company_id);
CREATE INDEX idx_invoice_items_company_id ON invoice_items(company_id);
CREATE INDEX idx_payments_company_id ON payments(company_id);
CREATE INDEX idx_reminder_logs_company_id ON reminder_logs(company_id);
